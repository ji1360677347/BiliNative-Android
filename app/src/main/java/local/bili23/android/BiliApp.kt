package local.bili23.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import local.bili23.android.core.BiliClient
import local.bili23.android.core.Http
import local.bili23.android.core.Store
import okhttp3.OkHttpClient

class BiliApp : Application(), ImageLoaderFactory {

    lateinit var store: Store
        private set
    lateinit var client: BiliClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = Store(this)
        client = BiliClient(store)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { client.ensureDevice() }
        }
    }

    /** 封面图与接口共用 UA / Referer，避免部分 CDN 返回 403。 */
    override fun newImageLoader(): ImageLoader {
        val imageClient: OkHttpClient = Http.imageClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", Http.UA)
                        .header("Referer", Http.REFERER)
                        .build()
                )
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(imageClient)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.2).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(120)
            .build()
    }

    companion object {
        lateinit var instance: BiliApp
            private set

        val store: Store get() = instance.store
        val client: BiliClient get() = instance.client
    }
}
