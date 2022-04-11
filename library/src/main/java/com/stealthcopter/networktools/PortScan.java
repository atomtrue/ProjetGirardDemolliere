import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PortScan {

    private static final int TIMEOUT_LOCALHOST = 25;
    private static final int TIMEOUT_LOCALNETWORK = 1000;
    private static final int TIMEOUT_REMOTE = 2500;

    private static final int DEFAULT_THREADS_LOCALHOST = 7;
    private static final int DEFAULT_THREADS_LOCALNETWORK = 50;
    private static final int DEFAULT_THREADS_REMOTE = 50;

    private static final int METHOD_TCP = 0;
    private static final int METHOD_UDP = 1;

    private int method = METHOD_TCP;
    private int noThreads = 50;
    private InetAddress address;
    private int timeOutMillis = 1000;
    private boolean cancelled = false;

    private ArrayList<Integer> ports = new ArrayList<>();
    private ArrayList<Integer> openPortsFound = new ArrayList<>();

    private PortListener portListener;

    private PortScan() {
    }

    public interface PortListener {
        void onResult(int portNo, boolean open);

        void onFinished(ArrayList<Integer> openPorts);
    }


    public static PortScan onAddress(String address) throws UnknownHostException {
        return onAddress(InetAddress.getByName(address));
    }


    public static PortScan onAddress(InetAddress ia) {
        PortScan portScan = new PortScan();
        portScan.setAddress(ia);
        portScan.setDefaultThreadsAndTimeouts();
        return portScan;
    }

    public PortScan setTimeOutMillis(int timeOutMillis) {
        if (timeOutMillis < 0) throw new IllegalArgumentException("Timeout cannot be less than 0");
        this.timeOutMillis = timeOutMillis;
        return this;
    }


    public PortScan setPort(int port) {
        ports.clear();
        validatePort(port);
        ports.add(port);
        return this;
    }


    public PortScan setPorts(ArrayList<Integer> ports) {

        // Check all ports are valid
        for (Integer port : ports) {
            validatePort(port);
        }

        this.ports = ports;

        return this;
    }


    public PortScan setPorts(String portString) {

        ports.clear();

        ArrayList<Integer> ports = new ArrayList<>();

        if (portString == null) {
            throw new IllegalArgumentException("Empty port string not allowed");
        }

        portString = portString.substring(portString.indexOf(":") + 1, portString.length());

        for (String x : portString.split(",")) {
            if (x.contains("-")) {
                int start = Integer.parseInt(x.split("-")[0]);
                int end = Integer.parseInt(x.split("-")[1]);
                validatePort(start);
                validatePort(end);
                if (end <= start)
                    throw new IllegalArgumentException("Start port cannot be greater than or equal to the end port");

                for (int j = start; j <= end; j++) {
                    ports.add(j);
                }
            } else {
                int start = Integer.parseInt(x);
                validatePort(start);
                ports.add(start);
            }
        }

        this.ports = ports;

        return this;
    }

    private void validatePort(int port) {
        if (port < 1) throw new IllegalArgumentException("Start port cannot be less than 1");
        if (port > 65535) throw new IllegalArgumentException("Start cannot be greater than 65535");
    }


    public PortScan setPortsPrivileged() {
        ports.clear();
        for (int i = 1; i < 1024; i++) {
            ports.add(i);
        }
        return this;
    }


    public PortScan setPortsAll() {
        ports.clear();
        for (int i = 1; i < 65536; i++) {
            ports.add(i);
        }
        return this;
    }

    private void setAddress(InetAddress address) {
        this.address = address;
    }

    private void setDefaultThreadsAndTimeouts() {

        if (IPTools.isIpAddressLocalhost(address)) {
   
            timeOutMillis = TIMEOUT_LOCALHOST;
            noThreads = DEFAULT_THREADS_LOCALHOST;
        } else if (IPTools.isIpAddressLocalNetwork(address)) {

            timeOutMillis = TIMEOUT_LOCALNETWORK;
            noThreads = DEFAULT_THREADS_LOCALNETWORK;
        } else {

            timeOutMillis = TIMEOUT_REMOTE;
            noThreads = DEFAULT_THREADS_REMOTE;
        }
    }

    public PortScan setNoThreads(int noThreads) throws IllegalArgumentException {
        if (noThreads < 1) throw new IllegalArgumentException("Cannot have less than 1 thread");
        this.noThreads = noThreads;
        return this;
    }



    private PortScan setMethod(int method) {
        switch (method) {
            case METHOD_UDP:
            case METHOD_TCP:
                this.method = method;
                break;
            default:
                throw new IllegalArgumentException("Invalid method type " + method);
        }
        return this;
    }


    public PortScan setMethodUDP() {
        setMethod(METHOD_UDP);
        return this;
    }

    public PortScan setMethodTCP() {
        setMethod(METHOD_TCP);
        return this;
    }


    public void cancel() {
        this.cancelled = true;
    }

    public ArrayList<Integer> doScan() {

        cancelled = false;
        openPortsFound.clear();

        ExecutorService executor = Executors.newFixedThreadPool(noThreads);

        for (int portNo : ports) {
            Runnable worker = new PortScanRunnable(address, portNo, timeOutMillis, method);
            executor.execute(worker);
        }

        executor.shutdown();
        try {
            executor.awaitTermination(1, TimeUnit.HOURS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Collections.sort(openPortsFound);

        return openPortsFound;
    }

    public PortScan doScan(final PortListener portListener) {

        this.portListener = portListener;
        openPortsFound.clear();
        cancelled = false;

        new Thread(new Runnable() {
            @Override
            public void run() {

                ExecutorService executor = Executors.newFixedThreadPool(noThreads);

                for (int portNo : ports) {
                    Runnable worker = new PortScanRunnable(address, portNo, timeOutMillis, method);
                    executor.execute(worker);
                }

                executor.shutdown();
                try {
                    executor.awaitTermination(1, TimeUnit.HOURS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                if (portListener != null) {
                    Collections.sort(openPortsFound);
                    portListener.onFinished(openPortsFound);
                }

            }
        }).start();

        return this;
    }

    private synchronized void portScanned(int port, boolean open) {
        if (open) {
            openPortsFound.add(port);
        }
        if (portListener != null) {
            portListener.onResult(port, open);
        }
    }

    private class PortScanRunnable implements Runnable {
        private final InetAddress address;
        private final int portNo;
        private final int timeOutMillis;
        private final int method;

        PortScanRunnable(InetAddress address, int portNo, int timeOutMillis, int method) {
            this.address = address;
            this.portNo = portNo;
            this.timeOutMillis = timeOutMillis;
            this.method = method;
        }

        @Override
        public void run() {
            if (cancelled) return;

            switch (method) {
                case METHOD_UDP:
                    portScanned(portNo, PortScanUDP.scanAddress(address, portNo, timeOutMillis));
                    break;
                case METHOD_TCP:
                    portScanned(portNo, PortScanTCP.scanAddress(address, portNo, timeOutMillis));
                    break;
                default:
                    throw new IllegalArgumentException("Invalid method");
            }
        }
    }


}
