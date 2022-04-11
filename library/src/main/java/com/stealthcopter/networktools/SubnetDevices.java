import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SubnetDevices {
    private int noThreads = 100;

    private ArrayList<String> addresses;
    private ArrayList<Device> devicesFound;
    private OnSubnetDeviceFound listener;
    private int timeOutMillis = 2500;
    private boolean cancelled = false;

    private boolean disableProcNetMethod = false;
    private HashMap<String, String> ipMacHashMap = null;

    private SubnetDevices() {
    }

    public interface OnSubnetDeviceFound {
        void onDeviceFound(Device device);

        void onFinished(ArrayList<Device> devicesFound);
    }

    public static SubnetDevices fromLocalAddress() {
        InetAddress ipv4 = IPTools.getLocalIPv4Address();

        if (ipv4 == null) {
            throw new IllegalAccessError("Could not access local ip address");
        }

        return fromIPAddress(ipv4.getHostAddress());
    }

    public static SubnetDevices fromIPAddress(InetAddress inetAddress) {
        return fromIPAddress(inetAddress.getHostAddress());
    }

    public static SubnetDevices fromIPAddress(final String ipAddress) {

        if (!IPTools.isIPv4Address(ipAddress)) {
            throw new IllegalArgumentException("Invalid IP Address");
        }

        String segment = ipAddress.substring(0, ipAddress.lastIndexOf(".") + 1);

        SubnetDevices subnetDevice = new SubnetDevices();

        subnetDevice.addresses = new ArrayList<>();

        for(String ip : ARPInfo.getAllIPAddressesInARPCache()) {
            if (ip.startsWith(segment)) {
                subnetDevice.addresses.add(ip);
            }
        }

        for (int j = 0; j < 255; j++) {
            if (!subnetDevice.addresses.contains(segment + j)) {
                subnetDevice.addresses.add(segment + j);
            }
        }

        return subnetDevice;

    }


    public static SubnetDevices fromIPList(final List<String> ipAddresses) {

        SubnetDevices subnetDevice = new SubnetDevices();

        subnetDevice.addresses = new ArrayList<>();

        subnetDevice.addresses.addAll(ipAddresses);

        return subnetDevice;

    }

    public SubnetDevices setNoThreads(int noThreads) throws IllegalArgumentException {
        if (noThreads < 1) throw new IllegalArgumentException("Cannot have less than 1 thread");
        this.noThreads = noThreads;
        return this;
    }

    public SubnetDevices setTimeOutMillis(int timeOutMillis) throws IllegalArgumentException {
        if (timeOutMillis < 0) throw new IllegalArgumentException("Timeout cannot be less than 0");
        this.timeOutMillis = timeOutMillis;
        return this;
    }

    public void setDisableProcNetMethod(boolean disable) {
        this.disableProcNetMethod = disableProcNetMethod;
    }
    public void cancel() {
        this.cancelled = true;
    }

    public SubnetDevices findDevices(final OnSubnetDeviceFound listener) {

        this.listener = listener;

        cancelled = false;
        devicesFound = new ArrayList<>();

        new Thread(new Runnable() {
            @Override
            public void run() {

                ipMacHashMap = disableProcNetMethod ? ARPInfo.getAllIPandMACAddressesFromIPSleigh() : ARPInfo.getAllIPAndMACAddressesInARPCache();

                ExecutorService executor = Executors.newFixedThreadPool(noThreads);

                for (final String add : addresses) {
                    Runnable worker = new SubnetDeviceFinderRunnable(add);
                    executor.execute(worker);
                }

                executor.shutdown();
                try {
                    executor.awaitTermination(1, TimeUnit.HOURS);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                ipMacHashMap = disableProcNetMethod ? ARPInfo.getAllIPandMACAddressesFromIPSleigh() : ARPInfo.getAllIPAndMACAddressesInARPCache();
                for (Device device : devicesFound) {
                    if (device.mac == null && ipMacHashMap.containsKey(device.ip)) {
                        device.mac = ipMacHashMap.get(device.ip);
                    }
                }


                listener.onFinished(devicesFound);

            }
        }).start();

        return this;
    }

    private synchronized void subnetDeviceFound(Device device) {
        devicesFound.add(device);
        listener.onDeviceFound(device);
    }

    public class SubnetDeviceFinderRunnable implements Runnable {
        private final String address;

        SubnetDeviceFinderRunnable(String address) {
            this.address = address;
        }

        @Override
        public void run() {

            if (cancelled) return;

            try {
                InetAddress ia = InetAddress.getByName(address);
                PingResult pingResult = Ping.onAddress(ia).setTimeOutMillis(timeOutMillis).doPing();
                if (pingResult.isReachable) {
                    Device device = new Device(ia);

                    if (ipMacHashMap.containsKey(ia.getHostAddress())) {
                        device.mac = ipMacHashMap.get(ia.getHostAddress());
                    }

                    device.time = pingResult.timeTaken;
                    subnetDeviceFound(device);
                }
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }
    }

}
