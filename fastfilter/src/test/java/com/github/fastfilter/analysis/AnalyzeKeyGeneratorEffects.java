package com.github.fastfilter.analysis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.function.Function;

/**
 * Analyze the effect of using different key generators (random, sequential,...)
 * on results.
 */
public class AnalyzeKeyGeneratorEffects {

/*

Run as follows:

#!/bin/sh
for rnd in `seq 0 128`; do
  echo ${now} rnd ${rnd};
  ./bulk-insert-and-query.exe 10000000 -1 ${rnd};
done > results-rnd-2018-10-18.txt

nohup ./test-rnd.sh &

 */

    final static String homeDir = System.getProperty("user.home");

    final static HashMap<String, Integer> namesMap = new HashMap<>();

    private int getAlgorithmId(String name) {
        Integer id = namesMap.get(name);
        if (id == null) {
            id = namesMap.size();
            algorithmNames[id] = name;
            namesMap.put(name, id);
        }
        return id;
    }

    int randomAlgorithm;
    String[] dataLine;
    static String[] algorithmNames = new String[100];
    ArrayList<Data> allData = new ArrayList<>();

    public static void main(String... args) throws IOException {
        new AnalyzeKeyGeneratorEffects().processFile();
    }

    private void processFile() throws IOException {
        LineNumberReader r = new LineNumberReader(new BufferedReader(new FileReader(homeDir +
                "/temp/results-rnd.txt")));
        randomAlgorithm = -1;
        while (true) {
            String line = r.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            // System.out.println(line);
            if (line.isEmpty() || line.startsWith("find")
                    || line.startsWith("ns/add")
                    || line.startsWith("WARNING")
                    || line.startsWith("Sort")
                    || line.startsWith("Using")) {
                continue;
            }
            String[] list = line.split(" +");
            if (line.startsWith("rnd")) {
                // eg. "rnd 2"
                randomAlgorithm = Integer.parseInt(list[1]);
                continue;
            }
            dataLine = list;
            processEntry();
        }
        r.close();
        verify();
    }

    static class Data {
        String algorithmName;
        int algorithmId;
        int randomAlgorithm;
        double add, find0, find25, find50, find75, find100;
        double e, bitsItem, optBitsItem, wastedSpace, keysMillions;
        boolean failed;

        public String toString() {
            return "alg " + algorithmNames[algorithmId] + " rnd " + randomAlgorithm + " add " + add + " f0 "
                    + find0 + " f25 " + find25 + " f75 " + find75 + " f100 " + find100 + " e " + e + " bitItem "
                    + bitsItem + " opt " + optBitsItem + " waste " + wastedSpace + " keys " + keysMillions + " failed "
                    + failed + "";
        }

    }

    private void verify() {
        for(int i=0; i<algorithmNames.length; i++) {
            if (algorithmNames[i] == null) {
                continue;
            }
            String name = algorithmNames[i];
            ArrayList<Data> data = new ArrayList<>();
            for(Data d : allData) {
                if (d.algorithmName.equals(name)) {
                    data.add(d);
                }
            }
            compareDataOfOneAlgorithm(data);
        }
    }

    private void compareDataOfOneAlgorithm(ArrayList<Data> list) {
        Data result = new Data();
        Data first = list.get(0);
        System.out.print(first.algorithmName);
        result.algorithmId = first.algorithmId;
        result.algorithmName = first.algorithmName;
        result.randomAlgorithm = first.randomAlgorithm;
        result.keysMillions = first.keysMillions;
        result.add = combineData(list, 50, "add", (d) -> d.add);
        result.find0 = combineData(list, 50, "find0", (d) -> d.find0);
        result.find25 = combineData(list, 50, "find25", (d) -> d.find25);
        result.find50 = combineData(list, 50, "find50", (d) -> d.find50);
        result.find75 = combineData(list, 50, "find75", (d) -> d.find75);
        result.find100 = combineData(list, 50, "find100", (d) -> d.find100);
        result.e = combineData(list, 20, "e", (d) -> d.e);
        result.bitsItem = combineData(list, 0.5, "bitsItem", (d) -> d.bitsItem);
        result.optBitsItem = combineData(list, 20, "optBitsItem", (d) -> d.optBitsItem);
        result.wastedSpace = combineData(list, 20, "wastedSpace", (d) -> d.wastedSpace);
        System.out.println();
    }

    private double combineData(ArrayList<Data> list, double maxPercentDiff,
            String param, Function<Data, Double> f) {
        double[] x = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            x[i] = f.apply(list.get(i));
        }
        Arrays.sort(x);
        double median = x[x.length / 2];
        if ("e".equals(param)) {
            System.out.print("  e median " + median + " min " + x[0] + " max " + x[x.length - 1]);
        }
        double diff1 = Math.abs(x[0] - median);
        double diff2 = Math.abs(x[x.length - 1] - median);
        double off1 = 100. / median * x[0];
        double off2 = 100. / median * x[x.length - 1];
        if (diff1 > maxPercentDiff * median / 100 && diff2 > maxPercentDiff * median / 100) {
            System.out.println("  " + param + " +/- > " + maxPercentDiff + "%: " +
                    Math.round(off1) + "%.." + Math.round(off2) + "%");
            System.out.println("       " + Arrays.toString(x));
        }
        return median;
    }

    private void processEntry() {
        if (randomAlgorithm < 0) {
            // no data
            throw new AssertionError();
        }
        if (dataLine == null) {
            // no data
            throw new AssertionError();
        }
        Data data = new Data();
        data.algorithmName = dataLine[0];
        data.algorithmId = getAlgorithmId(data.algorithmName);
        data.randomAlgorithm = randomAlgorithm;
        data.add = Double.parseDouble(dataLine[1]);
        data.find0 = Double.parseDouble(dataLine[2]);
        data.find25 = Double.parseDouble(dataLine[3]);
        data.find50 = Double.parseDouble(dataLine[4]);
        data.find75 = Double.parseDouble(dataLine[5]);
        data.find100 = Double.parseDouble(dataLine[6]);
        data.e = Double.parseDouble(dataLine[7].substring(0, dataLine[7].length() - 1));
        data.bitsItem = Double.parseDouble(dataLine[8]);
        data.optBitsItem = Double.parseDouble(dataLine[9]);
        data.wastedSpace = Double.parseDouble(dataLine[10].substring(0, dataLine[10].length() - 1));
        data.keysMillions = Double.parseDouble(dataLine[11]);
        allData.add(data);
        dataLine = null;
    }

}
