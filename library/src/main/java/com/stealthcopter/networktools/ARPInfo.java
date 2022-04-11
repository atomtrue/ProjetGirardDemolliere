
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;



public class ARPInfo {

    private ARPInfo() {
    }


    public static String getMACFromIPAddress(String ip) {
        if (ip == null) {
            return null;
        }

        HashMap<String, String> cache = getAllIPAndMACAddressesInARPCache();
        return cache.get(ip);
    }



    public static String getIPAddressFromMAC(String macAddress) {
        if (macAddress == null) {
            return null;
        }

        if (!macAddress.matches("..:..:..:..:..:..")) {
            throw new IllegalArgumentException("Invalid MAC Address");
        }

        HashMap<String, String> cache = getAllIPAndMACAddressesInARPCache();
        for (String ip : cache.keySet()) {
            if (cache.get(ip).equalsIgnoreCase(macAddress)) {
                return ip;
            }
        }
        return null;
    }



    public static ArrayList<String> getAllIPAddressesInARPCache() {
        return new ArrayList<>(getAllIPAndMACAddressesInARPCache().keySet());
    }


    public static ArrayList<String> getAllMACAddressesInARPCache() {
        return new ArrayList<>(getAllIPAndMACAddressesInARPCache().values());
    }



    public static HashMap<String, String> getAllIPAndMACAddressesInARPCache() {
        HashMap<String, String> macList = getAllIPandMACAddressesFromIPSleigh();
        for (String line : getLinesInARPCache()) {
            String[] splitted = line.split(" +");
            if (splitted.length >= 4) {
                // Ignore values with invalid MAC addresses
                if (splitted[3].matches("..:..:..:..:..:..")
                        && !splitted[3].equals("00:00:00:00:00:00")) {
                    if (!macList.containsKey(splitted[0])) {
                        macList.put(splitted[0], splitted[3]);
                    }
                }
            }
        }
        return macList;
    }


    private static ArrayList<String> getLinesInARPCache() {
        ArrayList<String> lines = new ArrayList<>();

        if (!new File("/proc/net/arp").canRead()){
            return lines;
        }

        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/net/arp"));
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return lines;
    }


    public static HashMap<String, String> getAllIPandMACAddressesFromIPSleigh() {
        HashMap<String, String> macList = new HashMap<>();

        try {
            Runtime runtime = Runtime.getRuntime();
            Process proc = runtime.exec("ip neigh show");
            proc.waitFor();
            int exit = proc.exitValue();

            InputStreamReader reader = new InputStreamReader(proc.getInputStream());
            BufferedReader buffer = new BufferedReader(reader);
            String line;
            while ((line = buffer.readLine()) != null) {
                String[] splits = line.split(" ");
                if (splits.length < 4) {
                    continue;
                }
                macList.put(splits[0], splits[4]);
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        return macList;
    }

}
