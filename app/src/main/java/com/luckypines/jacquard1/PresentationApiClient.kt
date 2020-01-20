import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyManagementException
import java.security.NoSuchAlgorithmException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.*


class PresentationApiClient {
  private fun disableSSLCertificateChecking() {
    val hostnameVerifier = object: HostnameVerifier {
      override fun verify(s:String, sslSession: SSLSession):Boolean {
        return true
      }
    }
    val trustAllCerts = arrayOf<TrustManager>(object: X509TrustManager {
      override fun getAcceptedIssuers(): Array<X509Certificate> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
      }

      //val acceptedIssuers:Array<X509Certificate> = null
      @Throws(CertificateException::class)
      override fun checkClientTrusted(arg0:Array<X509Certificate>, arg1:String) {// Not implemented
      }
      @Throws(CertificateException::class)
      override fun checkServerTrusted(arg0:Array<X509Certificate>, arg1:String) {// Not implemented
      }
    })
    try
    {
      val sc = SSLContext.getInstance("TLS")
      sc.init(null, trustAllCerts, java.security.SecureRandom())
      HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory())
      HttpsURLConnection.setDefaultHostnameVerifier(hostnameVerifier)
    }
    catch (e: KeyManagementException) {
      e.printStackTrace()
    }
    catch (e: NoSuchAlgorithmException) {
      e.printStackTrace()
    }
  }

  fun call(urlString: String, data: String) {
//    disableSSLCertificateChecking()
    val url = URL(urlString)
    val urlConnection: HttpURLConnection = url.openConnection() as HttpURLConnection
    try {
      urlConnection.setDoOutput(true)
      urlConnection.setChunkedStreamingMode(0)
      urlConnection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
      val outData = "command=$data"
      urlConnection.getOutputStream().use({ os ->
        val input: ByteArray = outData.toByteArray()
        os.write(input, 0, input.size)
      })
      BufferedReader(
        InputStreamReader(urlConnection.getInputStream(), "utf-8")
      ).use({ br ->
        val response = StringBuilder()
        var responseLine: String? = null
        while (br.readLine().also({ responseLine = it }) != null) {
          response.append(responseLine!!.trim { it <= ' ' })
        }
        Log.d(">>>>", response.toString())
      })
    } finally {
      urlConnection.disconnect()
    }
  }
}