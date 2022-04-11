
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

public class PortScanTCP {

    private PortScanTCP() {
    }
    public static boolean scanAddress(InetAddress ia, int portNo, int timeoutMillis) {

        Socket s = null;
        try {
            s = new Socket();
            s.connect(new InetSocketAddress(ia, portNo), timeoutMillis);
            return true;
        } catch (IOException e) {
        } finally {
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

}
