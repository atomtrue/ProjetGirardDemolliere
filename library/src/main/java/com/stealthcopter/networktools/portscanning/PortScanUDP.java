import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class PortScanUDP {

    private PortScanUDP() {
    }

    public static boolean scanAddress(InetAddress ia, int portNo, int timeoutMillis) {

        try {
            byte[] bytes = new byte[128];
            DatagramPacket dp = new DatagramPacket(bytes, bytes.length);

            DatagramSocket ds = new DatagramSocket();
            ds.setSoTimeout(timeoutMillis);
            ds.connect(ia, portNo);
            ds.send(dp);
            ds.isConnected();
            ds.receive(dp);
            ds.close();

        } catch (SocketTimeoutException e) {
            return true;
        } catch (Exception ignore) {

        }

        return false;
    }

}
