/**
 * Нужен для того, чтобы по своему реагировать на сообщения, дефолтная реализация - просто запуск колбека экшена
 */
class DefaultHandler(looper: Looper) : Handler {

	private val messageQueue: MessageQueue = looper.messageQueue

	override fun post(action: () -> Unit) {
		sendMessage(action.toMessage())
	}

	override fun postDelayed(delayMillis: Long, action: () -> Unit) {
		sendMessage(action.toMessage(delayMillis))
	}

	override fun sendMessage(message: Message) {
		messageQueue.add(message)
	}

	override fun handleMessage(message: Message) {
		message.action.invoke()
	}

	private fun (() -> Unit).toMessage(delayMillis: Long? = null): Message = DefaultMessage(
		target = this@DefaultHandler,
		tag = "todo",
		action = this,
		timeMillis = uptimeMillis() + (delayMillis ?: 0)
	)
}

open class DefaultMessage(
	override val target: Handler,
	override val tag: String,
	override val action: () -> Unit,
	override val timeMillis: Long = uptimeMillis()
) : Message {
	override fun compareTo(other: Message): Int = timeMillis.compareTo(other.timeMillis)
}

fun Handler(looper: Looper = Looper.myLooper): Handler = DefaultHandler(looper)