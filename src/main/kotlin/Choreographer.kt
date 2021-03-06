import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val REFRESH_RATE = 60 //FPS

/**
 * if N frames was skipped then show warning
 */
private const val SKIPPED_FRAME_WARNING_LIMIT = 30

/**
 * The default amount of time in ms between animation frames.
 * When vsync is not enabled, we want to have some idea of how long we should
 * wait before posting the next animation message.  It is important that the
 * default value be less than the true inter-frame delay on all devices to avoid
 * situations where we might skip frames by waiting too long (we must compensate
 * for jitter and hardware variations).  Regardless of this value, the animation
 * and display loop is ultimately rate-limited by how fast new graphics buffers can
 * be dequeued.
 */
private const val DEFAULT_FRAME_DELAY: Long = 10

private const val NANOS_PER_MS: Long = 1000000

/**
 * Абстракция над рендерером, управляет шедулингом колбеков.
 * Пример гейм лупа над которым работает хореографер https://gameprogrammingpatterns.com/game-loop.html
 *
 * todo дока
 * Это "хореографер", который позволяет подписываться на такты отрисовки фреймов, учитывая стратегии системных эвентов:
 * нажатий и тд
 * Имеет очередь traversals колбеков, которые шедулят пользователи([ViewRootImpl]) и которые poll'ятся при каждом кадре
 */
class Choreographer private constructor(looper: Looper) {

    companion object {
        private val threadChoreographer: ThreadLocal<Choreographer> = ThreadLocal.withInitial {
            val looper: Looper = Looper.myLooper
            return@withInitial Choreographer(looper)
        }

        fun getInstance(): Choreographer = threadChoreographer.get()
    }

    private var frameScheduled: Boolean = false
    private val frameIntervalNanos: Long = 1000000000L / REFRESH_RATE //16ms but in nanos
    private val actions: MutableList<Action> = mutableListOf()
    private var lastFrameTimeNanos: Long = 0

    private val handler = FrameHandler(looper)

    //todo покрыть синхронизацией, ибо эта штука может вызываться из другого потока
    fun postOnNextFrame(action: () -> Unit) {
        //todo need to use SystemClock.elapsedRealtimeNanos instead in android
        val nextFrameTime: Long = max(
            TimeUnit.NANOSECONDS.toMillis(lastFrameTimeNanos) + DEFAULT_FRAME_DELAY,
            TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
        )
        println("nextFrameMillis = $nextFrameTime, nanos = ${nextFrameTime * NANOS_PER_MS}, now = ${System.nanoTime()}")
        actions += Action(TimeUnit.MILLISECONDS.toNanos(nextFrameTime)) { action.invoke() }
        val msg: Message = ChoreographerMessage.FrameMessage(handler, "frame", action, nextFrameTime)
        handler.sendMessage(msg)
    }

    //todo сюда накинуть
    private fun doFrame(intendedFrameTimeNanos: Long) {
        var resultFrameTime: Long = intendedFrameTimeNanos
        val startNanos: Long = System.nanoTime()
        val jitterNanos: Long = startNanos - intendedFrameTimeNanos
        if (jitterNanos >= frameIntervalNanos) {
            val skippedFrames: Long = jitterNanos / frameIntervalNanos
            if (skippedFrames >= SKIPPED_FRAME_WARNING_LIMIT) {
                println(
                    "Choreographer: Skipped $skippedFrames frames!"
                            + "The application may be doing too much work on its main thread."
                )
            }
            val lastFrameOffset: Long = jitterNanos % frameIntervalNanos
            resultFrameTime = startNanos - lastFrameOffset
        }
        frameScheduled = false

        //todo убрать калбек тайпы и разделить на классы
        //doCallbacks(Choreographer.CALLBACK_INPUT, resultFrameTime)
        //mFrameInfo.markAnimationsStart()
        //doCallbacks(Choreographer.CALLBACK_ANIMATION, resultFrameTime)
        //doCallbacks(Choreographer.CALLBACK_INSETS_ANIMATION, resultFrameTime)
        //mFrameInfo.markPerformTraversalsStart()
        doCallbacks(/*Choreographer.CALLBACK_TRAVERSAL, */resultFrameTime)
        //doCallbacks(Choreographer.CALLBACK_COMMIT, resultFrameTime)
    }

    //todo thread safe
    private fun doCallbacks(frameTimeNanos: Long) {
        val now = System.nanoTime()
        val frameActions = actions.filter { it.scheduledNanoTime <= now }.ifEmpty { return }
        frameActions.forEach {
            it.callback.invoke(frameTimeNanos)
            actions.remove(it)
        }
    }

    private inner class FrameHandler(looper: Looper) : Handler by DefaultHandler(looper) {

        override fun handleMessage(message: Message) {
            when (message) {
                is ChoreographerMessage.FrameMessage -> {
                    doFrame(System.nanoTime())
                }
                else -> {
                    message.action.invoke()
                }
            }
        }

    }
}

sealed class ChoreographerMessage : Message {

    override fun compareTo(other: Message): Int = this.timeMillis.compareTo(other.timeMillis)

    class FrameMessage(
        override val target: Handler,
        override val tag: String,
        override val action: () -> Unit = {},
        override val timeMillis: Long = uptimeMillis()
    ) : ChoreographerMessage()
}

class Action(
    val scheduledNanoTime: Long,
    val callback: (startNanoTime: Long) -> Unit
)