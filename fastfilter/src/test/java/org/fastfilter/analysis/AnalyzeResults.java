package org.fastfilter.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.function.Function;

/**
 * Analyze C++ benchmark results.
 */
public class AnalyzeResults {

/*

Run the benchmark as follows:

git clone https://github.com/FastFilter/fastfilter_cpp.git

cd fastfilter_cpp
cd benchmarks
make clean; make
nohup ./benchmark.sh &

scp ...:~/fastfilter_cpp/benchmarks/benchmark-results.txt .

 */

    final static String homeDir = System.getProperty("user.home");

    final static HashMap<String, String> namesMap = new HashMap<>();
    final static HashMap<String, String> longNamesMap = new HashMap<>();
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
        }
    }

    int algorithmId = -1;
    long size;
    int randomAlgorithm;
    boolean warning;
    String[] dataLine;
    static String[] algorithmNames = new String[200];
    ArrayList<Data> allData = new ArrayList<>();
    ArrayList<Data> data = new ArrayList<>();

    public static void main(String... args) throws IOException {
        Locale.setDefault(Locale.ENGLISH);
        String resultFileName = homeDir + "/temp/benchmark-results.txt";
        if (args.length > 0) {
            resultFileName = args[0];
        }
        if (!new File(resultFileName).exists()) {
            throw new FileNotFoundException(resultFileName);
        }
        new AnalyzeResults().processFile(resultFileName);
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
            String[] list = line.split(" +");
            if (Character.isDigit(line.charAt(0)) && line.indexOf(" size ") >= 0) {
                processEntry();
                // eg. "14:56:21 alg 0 size 20 -1"
                algorithmId = Integer.parseInt(list[2]);
                size = Integer.parseInt(list[4]);
                randomAlgorithm = Integer.parseInt(list[5]);
                warning = false;
                continue;
            }
            if (line.startsWith("WARNING")) {
                warning = true;
                continue;
            }
            dataLine = list;
        }
        processEntry();
        r.close();
        System.out.println("=== construction =====");
        combineData(10);
        ArrayList<Data> c10 = new ArrayList<>(data);
        combineData(100);
        ArrayList<Data> c100 = new ArrayList<>(data);
        printConstructionTime(c10, c100);

        combineData(10);
        System.out.println("=== timevsspace10M-25-find.tikz ===");
        printQueryTimeVersusSpaceOverhead((d)-> {return nanosPerKey(d.find25);});
        System.out.println("=== timevsspace10M.tikz ===");
        printQueryTimeVersusSpaceOverhead((d)-> {return nanosPerKey(d.find100);});
        System.out.println("=== fppvsbitslog10.tikz ===");
        printFppVersusSpaceUsage();
        // printQueryTimeVersusSpaceUsage();
        combineData(100);
        System.out.println("=== timevsspace100M-25-find.tikz ===");
        printQueryTimeVersusSpaceOverhead((d)-> {return nanosPerKey(d.find25);});
        System.out.println("=== timevsspace100M.tikz ===");
        printQueryTimeVersusSpaceOverhead((d)-> {return nanosPerKey(d.find100);});
        System.out.println("=== fppvsbitslog100.tikz ===");
        printFppVersusSpaceUsage();
        System.out.println("=== all =====");
        System.out.println("\\begin{table}[]");
        System.out.println("\\small");
        System.out.println("\\begin{tabular}{lrrrrrrrrrr}");
        System.out.println("\\hline");
        System.out.println("Name & Constr. & \\multicolumn{5}{l}{Lookup} & bits & False & over- & Million \\\\");
        System.out.println(" & ns/key & 0\\% &  25\\% & 50\\% & 75\\% & 100\\% & /key & positives & head & keys \\\\");
        System.out.println("\\hline");
        combineData(10);
        listAll();
        combineData(100);
        listAll();
        System.out.println("\\hline");
        System.out.println("\\end{tabular}");
        System.out.println("\\end{table}");

        System.out.println("=== minimum =====");
        System.out.println("\\begin{table}[]");
        System.out.println("\\small");
        System.out.println("\\begin{tabular}{lrrrr}");
        System.out.println("\\hline");
        System.out.println("Name & Lookup (25\\% find) & Bits/key & FPP & Million keys \\\\");
        System.out.println("\\hline");
        combineData(10);
        listMinimum();
        combineData(100);
        listMinimum();
        System.out.println("\\hline");
        System.out.println("\\end{tabular}");
        System.out.println("\\end{table}");

        // printQueryTimeVersusSpaceUsage();
//        printQueryTimeVersusFPP();
//        printFppVersusSpaceOverhead();
//        printInsertTimes();
//        printFpp();
//        printSpaceOverhead();
//        printLookup25();
//        printLookup75();
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

    private void combineData(int keysMillions) {
        data.clear();
        for (int i = 0; i < allData.size(); i += 3) {
            Data[] tree = new Data[3];
            tree[0] = allData.get(i);
            tree[1] = allData.get(i + 1);
            tree[2] = allData.get(i + 2);
            if (tree[0].randomAlgorithm == 0) {
                continue;
            }
            if (tree[0].find100 == 0 || tree[1].find100 == 0 || tree[2].find100 == 0) {
                // System.out.println("missing entry at " + tree[0]);
            } else {
                if (tree[0].randomAlgorithm < 0) {
                    // TODO verify results with randomAlgorithm >= 0 match
                    Data combined = combineData(tree);
                    if (keysMillions < 0 || keysMillions == combined.keysMillions) {
                        data.add(combined);
                    }
                }
            }
        }
    }

    private Data combineData(Data[] list) {
        for (Data d : list) {
            if (d.failed) {
                return null;
            }
        }
        Data result = new Data();
        result.algorithmId = list[0].algorithmId;
        result.randomAlgorithm = list[0].randomAlgorithm;
        result.keysMillions = list[0].keysMillions;
        result.add = combineData(list, 3, (d) -> d.add);
        result.find0 = combineData(list, 3, (d) -> d.find0);
        result.find25 = combineData(list, 3, (d) -> d.find25);
        result.find50 = combineData(list, 3, (d) -> d.find50);
        result.find75 = combineData(list, 3, (d) -> d.find75);
        result.find100 = combineData(list, 3, (d) -> d.find100);
        result.e = combineData(list, 100, (d) -> d.e);
        result.bitsItem = combineData(list, 2, (d) -> d.bitsItem);
        result.optBitsItem = combineData(list, 10, (d) -> d.optBitsItem);
        result.wastedSpace = combineData(list, 50, (d) -> d.wastedSpace);
        return result;
    }

    private double combineData(Data[] list, double maxPercentDiff, Function<Data, Double> f) {
        double[] x = new double[list.length];
        for (int i = 0; i < list.length; i++) {
            x[i] = f.apply(list[i]);
        }
        if (x.length != 3) {
            throw new AssertionError();
        }
        Arrays.sort(x);
        double median = x[1];
        double diff1 = Math.abs(x[0] - median);
        double diff2 = Math.abs(x[2] - median);
        if (diff1 > maxPercentDiff * median / 100 && diff2 > maxPercentDiff * median / 100) {
            System.out.println("avg +/- > " + maxPercentDiff + "% " + Arrays.toString(x));
            System.out.println(list[0] + " -> " + nanosPerKey(x[0]));
            System.out.println(list[1] + " -> " + nanosPerKey(x[1]));
            System.out.println(list[2] + " -> " + nanosPerKey(x[2]));
        }
        return median;
    }

    private void processEntry() {
        if (algorithmId < 0) {
            return;
        }
        Data data = new Data();
        data.algorithmId = algorithmId;
        data.randomAlgorithm = randomAlgorithm;
        data.keysMillions = size;
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
            if (keys != data.keysMillions) {
                throw new AssertionError();
            }
        }
        // System.out.println(data);
        allData.add(data);
        dataLine = null;
    }

    static int removeHighest(double[] sort) {
        double max = 0;
        int best = -1;
        for (int i = 0; i < sort.length; i++) {
            double x = sort[i];
            if (x > max) {
                best = i;
                max = x;
            }
        }
        if (best >= 0) {
            sort[best] = 0;
        }
        return best;
    }

    private void printConstructionTime(ArrayList<Data> c10, ArrayList<Data> c100) {
        System.out.println("==== Construnction Time");
        sortByName(c10);
        sortByName(c100);
        for (int i = 0; i < c10.size(); i++) {
            Data d10 = c10.get(i);
            Data d100 = c100.get(i);
            String name = algorithmNames[d10.algorithmId];
            int i10 = (int) Math.round(nanosPerKey(d10.add) / 10.0) * 10;
            int i100 = (int) Math.round(nanosPerKey(d100.add) / 10.0) * 10;
            System.out.printf("%s & %d ns/key & %d ns/key \\\\\n",
                    longNamesMap.get(name), i10, i100);
        }
    }

    private void printQueryTimeVersusSpaceOverhead(Function<Data, Double> getter) {
        sortByName(data);
        String lastType = null;
        int color = 0;
        ArrayList<String> typeLines = new ArrayList<>();
        String typeList = "";
        System.out.println("waste ns  label color align");
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (!listAlgorithm(name)) {
                continue;
            }
            if (lastType == null) {
                lastType = d.getType();
            }
            if (!d.getType().equals(lastType)) {
                typeLines.add(typeList);
                typeLines.add(lastType);
                typeList = "";
                color++;
                lastType = d.getType();
            }
            int align = 0;
            name = d.getName();
            if (name.startsWith("Cuckoo")) {
                name = "C" + name.substring(6);
                align = -90;
            } else if (name.startsWith("Css")) {
                align = 180;
            } else if (name.startsWith("Bloom")) {
                align = 180;
            } else if (name.equals("Xor8")) {
                align = 90;
            }
            double x = getter.apply(d);
            System.out.println(d.wastedSpace + " " + x + " " +
                    name + " " + color + " " + align);
            typeList += "(" + d.wastedSpace + ", " + x + ")";
        }
        typeLines.add(typeList);
        typeLines.add(lastType);
        for(String s : typeLines) {
            if (s.startsWith("(")) {
                System.out.println("    \\addplot plot coordinates {" + s + "};");
            } else {
                System.out.println("    \\addlegendentry{" + s + "}");
            }
        }
    }

    private void sortByName(ArrayList<Data> data) {
        data.sort((o1, o2) -> {
            int result = o1.getType().compareTo(o2.getType());
            return result == 0 ? -Double.compare(o1.e, o2.e) : result;
        });
    }

    private void printFppVersusSpaceUsage() {
        sortByName(data);
        String lastType = null;
        int color = 0;
        ArrayList<String> typeLines = new ArrayList<>();
        String typeList = "";
        System.out.println("bits/key fpp label color align");
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (!listAlgorithm(name)) {
                continue;
            }
            if (lastType == null) {
                lastType = d.getType();
            }
            if (!d.getType().equals(lastType)) {
                typeLines.add(typeList);
                typeLines.add(lastType);
                typeList = "";
                color++;
                lastType = d.getType();
            }
            int align = 180;
            name = d.getName();
            if (name.startsWith("Cuckoo")) {
                name = "C" + name.substring(6);
            } else if (name.startsWith("Bloom8")) {
                align = 45;
            } else if (name.startsWith("Bloom12")) {
                align = 220;
            } else if (name.startsWith("Bloom16")) {
                align = 180;
            } else if (name.startsWith("Xor")) {
                align = 45;
            }
            double x = d.e;
            System.out.println(d.bitsItem + " " + x + " " +
                    name + " " + color + " " + align);
            typeList += "(" + d.bitsItem + ", " + x + ")";
        }
        typeLines.add(typeList);
        typeLines.add(lastType);
        for(String s : typeLines) {
            if (s.startsWith("(")) {
                System.out.println("    \\addplot plot coordinates {" + s + "};");
            } else {
                System.out.println("    \\addlegendentry{" + s + "}");
            }
        }
    }

    static boolean listAlgorithm(String name) {
        return true;
        // return !name.equals("GCS") && !name.equals("CQF");
    }

    private void printEnd(String name) {
        System.out.println("};");
        System.out.println("    \\addlegendentry{" + name + "}");
    }

    private void printPlot() {
        System.out.print("    \\addplot plot coordinates {");
    }

    private void printData(double x, double y) {
        System.out.print("(" + x + ", " + y + ")");
    }

    static double nanosPerKey(double value) {
        return value;
        // for "million entries per second", use:
        // return Math.round(1 / value * 1000);
    }

    void listAll() {
        sortByName(data);
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            System.out.println(name +
                    " & " + Math.round(nanosPerKey(d.add)) +
                    " & " + Math.round(nanosPerKey(d.find0)) +
                    " & " + Math.round(nanosPerKey(d.find25)) +
                    " & " + Math.round(nanosPerKey(d.find50)) +
                    " & " + Math.round(nanosPerKey(d.find75)) +
                    " & " + Math.round(nanosPerKey(d.find100)) +
                    " & " + String.format("%.1f", d.bitsItem) +
                    " & " + String.format("%.3f", d.e) +
                    " & " + String.format("%.1f", d.wastedSpace) +
                    " & " + Math.round(d.keysMillions) +
                    " \\\\");
        }
    }

    void listMinimum() {
        sortByName(data);
        for(Data d : data) {
            String name = algorithmNames[d.algorithmId];
            if (name.endsWith("-addAll")) {
                continue;
            }
            System.out.println(name +
                    " & " + Math.round(nanosPerKey(d.find25)) +
                    " & " + String.format("%.1f", d.bitsItem) +
                    " & " + String.format("%.3f", d.e) +
                    " & " + Math.round(d.keysMillions) +
                    " \\\\");
        }
    }

}
