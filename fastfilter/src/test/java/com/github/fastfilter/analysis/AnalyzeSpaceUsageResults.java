package com.github.fastfilter.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

/**
 * Analyze C++ benchmark results.
 */
public class AnalyzeSpaceUsageResults {

/*

Run the space usage test as follows:

git clone https://github.com/FastFilter/fastfilter_cpp.git

cd fastfilter_cpp
cd benchmarks
make clean; make
nohup ./spaceUsage.sh &

scp ...:~/fastfilter_cpp/benchmarks/spaceUsage-results.txt .

 */

    final static String homeDir = System.getProperty("user.home");

    final static HashMap<String, String> namesMap = new HashMap<>();
    final static HashMap<String, String> longNamesMap = new HashMap<>();
    final static HashMap<String, Integer> algorithmIds = new HashMap<>();
    static {
        String[] map = {
                "CuckooSemiSort13", "Css13", "Cuckoo semiSort 13",
                "Cuckoo8", "Cuckoo8", "Cuckoo 8",
                "Cuckoo12", "Cuckoo12", "Cuckoo 12",
                "Cuckoo16", "Cuckoo16", "Cuckoo 16",
                "Cuckoo12-2^n", "OC12", "Original Cuckoo 12",
                "Cuckoo16-2^n", "OC16", "Original Cuckoo 16",
                "CuckooSemiSort13-2^n", "OCss13",  "Original Cuckoo semiSort 13",
                "Xor8", "Xor8", "Xor 8",
                "Xor12", "Xor12", "Xor 12",
                "Xor16", "Xor16", "Xor 16",
                "CQF", "CQF", "Counting quotient filter",
                "Bloom8", "Bloom8", "Bloom 8",
                "Bloom12", "Bloom12", "Bloom 12",
                "Bloom16", "Bloom16", "Bloom 16",
                "BlockedBloom", "BlockedBloom", "BlockedBloom",
                "BlockedBloom-addAll", "BlockedBloom addAll", "BlockedBloom addAll",
                "GCS", "GCS", "Golomb compressed set",
                "Xor+8", "Xor+8", "Xor+ 8",
                "Xor+16", "Xor+16", "Xor+ 16",
                "Morton", "Morton", "Morton 3/8",
                };
        for (int i = 0; i < map.length; i += 3) {
            namesMap.put(map[i], map[i + 1]);
            longNamesMap.put(map[i], map[i + 2]);
            algorithmIds.put(map[i], i);
        }
    }

    long size;
    int randomAlgorithm;
    boolean warning;
    String[] dataLine;
    static String[] algorithmNames = new String[200];
    ArrayList<Data> allData = new ArrayList<>();
    ArrayList<Data> data = new ArrayList<>();

    public static void main(String... args) throws IOException {
        Locale.setDefault(Locale.ENGLISH);
        String resultFileName = homeDir + "/temp/spaceUsage-results.txt";
        if (args.length > 0) {
            resultFileName = args[0];
        }
        if (!new File(resultFileName).exists()) {
            throw new FileNotFoundException(resultFileName);
        }
        new AnalyzeSpaceUsageResults().processFile(resultFileName);
    }

    private void processFile(String resultFileName) throws IOException {
        LineNumberReader r = new LineNumberReader(new BufferedReader(new FileReader(resultFileName)));
        while (true) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            // System.out.println(line);
            if (line.isEmpty() || line.startsWith("find") || line.startsWith("ns/add")
                    || line.startsWith("Using")) {
                continue;
            }
            line = line.replaceAll(" \\(addall\\)", "_addAll");
            if (line.startsWith("WARNING")) {
                warning = true;
                continue;
            }
            String[] list = line.split(" +");
            if (Character.isDigit(line.charAt(0)) && line.indexOf(" size ") >= 0) {
                continue;
            }
            dataLine = list;
            processEntry();
        }
        r.close();
        System.out.println("=== space overhead =====");
        HashMap<String, SpaceOverhead> overhead = new HashMap<>();
        ArrayList<Integer> sizes = new ArrayList<>();
        for (int size = 10; size <= 100; size++) {
            sizes.add(size);
            combineData(size / 10.);
            for(Data d : data) {
                String name = algorithmNames[d.algorithmId];
                SpaceOverhead o = overhead.get(name);
                if (o == null) {
                    o = new SpaceOverhead();
                    o.algorithmName = name;
                    overhead.put(name, o);
                }
                o.wastedSpace.add(d.wastedSpace);
                o.bitsPerKey.add(d.bitsItem);
            }
        }
        System.out.println("----- wasted space in % -----------");
        System.out.print("name ");
        for (int i = 0; i < sizes.size(); i++) {
            System.out.print(" " + sizes.get(i));
        }
        System.out.println();
        for (String n : algorithmNames) {
            if (n == null) {
                continue;
            }
            System.out.print(n);
            SpaceOverhead o = overhead.get(n);
            for (int i = 0; i < sizes.size(); i++) {
                System.out.print(" " + o.wastedSpace.get(i));
            }
            System.out.println();
        }
        System.out.println("----- bits/key -----------");
        for (String n : algorithmNames) {
            if (n == null) {
                continue;
            }
            System.out.print(n);
            SpaceOverhead o = overhead.get(n);
            for (int i = 0; i < sizes.size(); i++) {
                System.out.print(" " + o.bitsPerKey.get(i));
            }
            System.out.println();
        }
    }

    static class SpaceOverhead {
        String algorithmName;
        ArrayList<Double> wastedSpace = new ArrayList<>();
        ArrayList<Double> bitsPerKey = new ArrayList<>();
    }

    static class Data {
        int algorithmId;
        int randomAlgorithm;
        double add, remove, find0, find25, find50, find75, find100;
        double e, bitsItem, optBitsItem, wastedSpace, keysMillions;
        boolean failed;

        public String toString() {
            return "alg " + algorithmNames[algorithmId] + " rnd " + randomAlgorithm + " add " + add + " f0 "
                    + find0 + " f25 " + find25 + " f75 " + find75 + " f100 " + find100 + " e " + e + " bitItem "
                    + bitsItem + " opt " + optBitsItem + " waste " + wastedSpace + " keys " + keysMillions + " failed "
                    + failed + "";
        }

        public String getName() {
            String n = algorithmNames[algorithmId];
            String n2 = namesMap.get(n);
            return n2 == null ? n : n2;
        }

        public String getType() {
            String n = algorithmNames[algorithmId];
            String n2 = namesMap.get(n);
            n = n2 == null ? n : n2;
            n = n.replaceAll("[0-9]", "");
            return n;
        }

    }

    private void combineData(double keysMillions) {
        data.clear();
        for (int i = 0; i < allData.size(); i++) {
            Data d = allData.get(i);
            if (d.find100 == 0) {
                // System.out.println("missing entry at " + tree[0]);
            } else {
                // TODO verify results with randomAlgorithm >= 0 match
                if (keysMillions < 0 || keysMillions == d.keysMillions) {
                    data.add(d);
                }
            }
        }
    }

    private void processEntry() {
        Data data = new Data();
        data.randomAlgorithm = randomAlgorithm;
        // million find find find find find optimal wasted million
        // adds/sec 0% 25% 50% 75% 100% ε bits/item bits/item space keys
        // Xor12SplitMix/2 5.77 24.98 24.95 24.94 25.00 24.93 0.026% 14.76 11.89
        // 24.1% 60.0
        if (dataLine == null) {
            // no data
            // throw new AssertionError();
            data.failed = true;
        } else {
            String name = dataLine[0].replace('_', '-');
            if (!listAlgorithm(name)) {
                return;
            }
            if (!algorithmIds.containsKey(name)) {
                // throw new IllegalArgumentException("Unknown algorithm: " + name + " " + Arrays.toString(dataLine));
                return;
            }
            int algorithmId = algorithmIds.get(name);
            data.algorithmId = algorithmId;
            if (algorithmNames[algorithmId] == null) {
                algorithmNames[algorithmId] = name;
            } else if (!name.equals(algorithmNames[algorithmId])) {
                throw new AssertionError();
            }
            if (name.equals("Sort")) {
                System.out.println("Sort " + size + " millions: " + dataLine[2] + " ns/key");
                return;
            }
            data.add = Double.parseDouble(dataLine[1]);
            data.remove = Double.parseDouble(dataLine[2]);
            data.find0 = Double.parseDouble(dataLine[3]);
            data.find25 = Double.parseDouble(dataLine[4]);
            data.find50 = Double.parseDouble(dataLine[5]);
            data.find75 = Double.parseDouble(dataLine[6]);
            data.find100 = Double.parseDouble(dataLine[7]);
            data.e = Double.parseDouble(dataLine[8].substring(0, dataLine[8].length() - 1));
            data.bitsItem = Double.parseDouble(dataLine[9]);
            data.optBitsItem = Double.parseDouble(dataLine[10]);
            data.wastedSpace = Double.parseDouble(dataLine[11].substring(0, dataLine[11].length() - 1));
            data.failed = warning;
            double keys = Double.parseDouble(dataLine[12]);
            data.keysMillions = keys;
        }
        // System.out.println(data);
        allData.add(data);
        dataLine = null;
    }

    private static boolean listAlgorithm(String name) {
        return true;
        // return !name.equals("GCS") && !name.equals("CQF");
    }

}
