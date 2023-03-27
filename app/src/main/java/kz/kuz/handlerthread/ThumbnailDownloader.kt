package kz.kuz.handlerthread

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

class ThumbnailDownloader<T>     // создаётся параллельный поток, в котором, однако,
// Handler из основного потока, все его сообщения обрабатываются в очереди основного потока
(  // Handler - субкласс HandlerThread, который управляет сообщениями
        private val mResponseHandler: Handler) : HandlerThread("ThumbnailDownloader") {
    // тип T позволяет использовать объекты любого типа
    // класс HandlerThread предоставляет класс Looper, который позволяет использовать сообщения
    // (Message) повторно в потоке
    private var mHasQuit = false
    private var mRequestHandler // объект параллельного потока
            : Handler? = null

    // mResponseHandler - экземпляр Handler, переданный из главного потока
    private val mRequestMap: ConcurrentMap<T, String> = ConcurrentHashMap()

    // ConcurrentHashMap - разновидность HashMap, безопасная по отношению к потокам
    private var mThumbnailDownloadListener: ThumbnailDownloadListener<T>? = null

    interface ThumbnailDownloadListener<T> {
        fun onThumbnailDownloaded(target: T, thumbnail: Bitmap?)
    // интерфейс для передачи ответов запрашивающей стороне (основному потоку)
    }

    fun setThumbnailDownloadListener(listener: ThumbnailDownloadListener<T>?) {
        mThumbnailDownloadListener = listener
        // из класса MainFragment устанавливается слушатель в класса ThumbnailDownloader
        // это необходимо для того, чтобы ThumbnailDownloader мог работать с разными типами файлов
        // данный слушатель срабатывает, когда изображение уже загружено
    }

    override fun onLooperPrepared() {
        // метод onLooperPrepared() вызывается до того, как Looper впервые проверить очередь,
        // поэтому он хорошо подходит для реализации Handler
        mRequestHandler = object : Handler(Looper.myLooper()!!) {
            // поэтому Handler всегда содержит ссылку на Looper
            // к одному Looper может быть присоединено несколько Handler
            // Looper обслуживает очередь сообщений Message разных Handler
            override fun handleMessage(msg: Message) {
                // handleMessage() вызывается, когда сообщение извлечено из очереди и готово к
                // обработке
                if (msg.what == 0) {
                    val target = msg.obj as T
                    handleRequest(target)
                }
            }
        }
    }

    override fun quit(): Boolean {
        mHasQuit = true
        return super.quit()
    }

    fun queueThumbnail(target: T, url: String?) {
        // target - это holder (MainHolder), view, где будет размещено изображение
        if (url == null) {
            mRequestMap.remove(target)
        } else {
            mRequestMap[target] = url
            mRequestHandler?.obtainMessage(0, target)?.sendToTarget()
            // метод obtainMessage() создаёт в Handler объект Message
            // при этом используется общий пул объектов, чтобы избежать создания новых объектов
            // в одном объекте Handler может быть создано несколько Message
            // 0 - код (int), присваеваемый сообщению для идентификации
            // метод sendToTarget() помещает сообщение в конец очереди Looper
            // когда Looper добирается до конкретного сообщения, он передаёт его для обработки
            // оно обрабатывается в Handler.handleMessage()
            // в Message URL адрес не входит, он вносится только в HashMap, чтобы гарантировать,
            // что для данного экземпляра holder всегда загружается нужный URL
            // это важно, потому что объекты ViewHolder и RecyclerView перерабатываются и
            // используются повторно
        }
    }

    fun clearQueue() {
        // метод очистки очереди
        mRequestHandler?.removeMessages(0)
        mRequestMap.clear()
    }

    private fun handleRequest(target: T) {
        try {
            val url = mRequestMap[target] ?: return
            val bitmapBytes = downloadPicture2(url) // загружаем изображение из Интернета
            val bitmap = BitmapFactory.decodeByteArray(bitmapBytes, 0,
                    bitmapBytes.size) // создаём bitmap
            mResponseHandler.post(Runnable
            // весь код run() будет выполнен в главном потоке, поскольку Looper mResponseHandler
            // работает в главном потоке
            {
                if (mRequestMap[target] !== url || mHasQuit) {
                    return@Runnable
                }
                // проверка requestMap необходима, потому что RecyclerView заново использует
                // свои представления. К тому времени, когда ThumbnailDownloader завершит
                // загрузку Bitmap, может оказаться, что RecyclerView уже переработал ImageView
                // и запросил для него изображение с другого URL. Таким образом гарантируется,
                // что каждый объект MainHolder получит правильное изображение, даже если за
                // прошедшее время был сделан другой запрос
                mRequestMap.remove(target)
                mThumbnailDownloadListener?.onThumbnailDownloaded(target, bitmap)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // подключение к Интернет и загрузка изображений
    // Handler реализуется в фоновом потоке, поэтому запускать отдельно параллельный поток не нужно
    @Throws(Exception::class)
    fun downloadPicture2(url: String?): ByteArray {
        val mUrl = URL(url)
        val connection = mUrl.openConnection() as HttpURLConnection
        val out = ByteArrayOutputStream()
        val `in` = connection.inputStream
//        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
//            throw IOException(connection.responseMessage + mUrl)
//        }
        var bytesRead: Int
        val buffer = ByteArray(1024)
        while (true) {
            if (`in`.read(buffer).also { bytesRead = it } <= 0) break
            out.write(buffer, 0, bytesRead)
        }
        out.close()
        return out.toByteArray()
    }
}