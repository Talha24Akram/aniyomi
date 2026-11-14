package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Fork: a stalled GET (read/connect timeout) is retried on a fresh connection instead of
 * failing the whole episode load. Only idempotent GET/HEAD requests are retried.
 */
class RetryOnTimeoutInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET" && request.method != "HEAD") return chain.proceed(request)
        var attempt = 0
        while (true) {
            try {
                return chain.proceed(request)
            } catch (e: SocketTimeoutException) {
                if (chain.call().isCanceled() || ++attempt > maxRetries) throw e
            } catch (e: IOException) {
                val timedOut = generateSequence<Throwable>(e) { it.cause }.any { it is SocketTimeoutException }
                if (!timedOut || chain.call().isCanceled() || ++attempt > maxRetries) throw e
            }
        }
    }
}
