import com.saman.tunnel.ConfigParser
import com.saman.tunnel.XrayConfig
import java.io.File

/** Test fixture converter; writes credentials to a temporary file, never stdout. */
fun main(args: Array<String>) {
    val profile = ConfigParser.parse(File(args[0]).readText()).single()
    File(args[1]).writeText(XrayConfig.build(profile).toString())
}
