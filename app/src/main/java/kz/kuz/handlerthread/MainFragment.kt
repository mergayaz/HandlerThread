package kz.kuz.handlerthread

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat.getDrawable
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kz.kuz.handlerthread.ThumbnailDownloader.ThumbnailDownloadListener
import java.util.*

// в данном упражнении организуется подгрузка изображений с Интернета по мере прокрутки RecyclerView
// добавлен запрос на Интернет в манифест
// специальным образом нужно настроить image_view.xml (по умолчанию изображения выводятся в
// маленьком размере)
class MainFragment : Fragment() {
    private lateinit var mMainRecyclerView: RecyclerView
    private lateinit var mThumbnailDownloader: ThumbnailDownloader<MainHolder>
    private val urlList: MutableList<String> = ArrayList()
    private val spicesArray = arrayOf("basil", "cardamom", "coriander", "cumin", "marjoram",
            "oregano", "paprika", "rosemary", "turmeric")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val responseHandler = Handler(Looper.getMainLooper())
        // создаём экземпляр Handler в основном потоке,
        // этот экземпляр связан с Looper в основном потоке
        mThumbnailDownloader = ThumbnailDownloader(responseHandler)
        // создаём параллельный поток и передаём ему Handler основного потока
        mThumbnailDownloader.setThumbnailDownloadListener(
                object : ThumbnailDownloadListener<MainHolder> {
                    override fun onThumbnailDownloaded(target: MainHolder, thumbnail: Bitmap?) {
                        val drawable: Drawable = BitmapDrawable(resources, thumbnail)
                        target.bind(drawable)
                    }
                } // использование слушателя передаёт ответственность за обработку загруженного
                // изображения классу MainFragment, тем самым задача загрузки отделяется от задачи
                // обновления пользовательского интерфейса, чтобы класс ThumbnailDownloader при
                // необходимости мог использоваться для загрузки других типов объектов View
        )
        mThumbnailDownloader.start() // запускаем поток
        mThumbnailDownloader.looper
        // getLooper() вызывается после start(), тем самым исключается теоретически возможная
        // (хотя и редкая) ситуация гонки (race condition)
        // без этого нет гарантии, что метод onLooperPrepared() был вызван, в этом случае
        // вызов queueThumbnail() завершится неудачей, так как ссылка на Handler равна null
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        activity?.setTitle(R.string.toolbar_title)
        val view = inflater.inflate(R.layout.fragment_main, container, false)
        mMainRecyclerView = view.findViewById(R.id.recycler_view)
        mMainRecyclerView.layoutManager = GridLayoutManager(activity, 3)
        for (j in 0..9) {
            for (i in spicesArray.indices) {
                urlList.add("https://spice.101.kz/images/images-200d/" + spicesArray[i] + ".jpg")
            }
        }
        mMainRecyclerView.setAdapter(MainAdapter(urlList))
        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mThumbnailDownloader.clearQueue()
        // очищаем очередь при уничтожении представления, иначе ThumbnailDownloader может стать
        // связанным с недействительными экземплярами MainHolder
    }

    override fun onDestroy() {
        super.onDestroy()
        mThumbnailDownloader.quit() // обязательно нужно закрыть поток
    }

    private inner class MainHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val mItemImageView: ImageView = itemView.findViewById(R.id.item_image_view)
        fun bind(drawable: Drawable?) {
            mItemImageView.setImageDrawable(drawable)
        }

    }

    private inner class MainAdapter(private val mUrls: List<String>) : RecyclerView.Adapter<MainHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MainHolder {
            val inflater = LayoutInflater.from(activity)
            val view = inflater.inflate(R.layout.image_view, parent, false)
            return MainHolder(view)
        }

        override fun onBindViewHolder(holder: MainHolder, position: Int) {
//            val placeholder = resources.getDrawable(R.drawable.pepper_black_corns)
            // закомментирован устаревший метод который, однако, работает
            val placeholder = activity?.let { getDrawable(it, R.drawable.pepper_black_corns) }
            // задаём изображение по умолчанию, которое выводится до тех пор, пока нужное
            // изображение не будет отображено
            holder.bind(placeholder)
            mThumbnailDownloader.queueThumbnail(holder, mUrls[position])
            // подключаем очередь Handler
            // при появлении нового holder (view) вызывается метод queueThumbnail
            // в объекте holder (MainHolder) будет размещено изображение
            // также передаётся URL для загрузки
        }

        override fun getItemCount(): Int {
            return mUrls.size
        }
    }
}