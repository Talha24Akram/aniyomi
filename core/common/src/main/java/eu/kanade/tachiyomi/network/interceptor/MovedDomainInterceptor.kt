package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.ConcurrentHashMap

/**
 * Some sites retire a mirror domain and 301 every URL on it to the *homepage* of the new domain
 * (e.g. reanime.to/api/x -> reanime.cz/), dropping the path. Extensions then get HTML where they
 * expect JSON and fail with cryptic parse errors on every request.
 *
 * When a redirect lands on the root of a different TLD of the same site name while the original
 * request targeted a deeper path, replay the original path on the new host (and remember the move).
 */
class MovedDomainInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        movedHosts[request.url.host]?.let { newHost ->
            request = request.newBuilder().url(request.url.newBuilder().host(newHost).build()).build()
        }
        val response = chain.proceed(request)

        val landed = response.request.url
        val origin = request.url
        if (landed.host != origin.host &&
            landed.encodedPath == "/" &&
            origin.encodedPath != "/" &&
            siteName(landed.host) == siteName(origin.host)
        ) {
            movedHosts[origin.host] = landed.host
            response.close()
            val retry = request.newBuilder().url(origin.newBuilder().host(landed.host).build()).build()
            return chain.proceed(retry)
        }
        return response
    }

    private fun siteName(host: String): String? = host.split('.').let { if (it.size >= 2) it[it.size - 2] else null }

    private companion object {
        val movedHosts = ConcurrentHashMap<String, String>()
    }
}
