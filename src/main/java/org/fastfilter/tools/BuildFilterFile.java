package org.fastfilter.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.nio.charset.Charset;
import java.util.ArrayList;

import org.fastfilter.utils.StringUtils;
import org.fastfilter.xorplus.XorPlus8;

public class BuildFilterFile {

    public static final int SEGMENT_BITS = 10;

    public static void main(String... args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java " + BuildFilterFile.class.getName() + " <filterFile>\n"
                    + "Reads a text file from standard in and writes a filter file.\n"
                    + "The input is a text file that contains sorted SHA-1 hashes and counts.\n"
                    + "You may get the file from https://haveibeenpwned.com/passwords.");
            return;
        }
        LineNumberReader lineReader = new LineNumberReader(
                new InputStreamReader(new BufferedInputStream(System.in), Charset.forName("LATIN1")));
        long start = System.nanoTime();
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(args[0])));
        long outPos = 0;
        int lines = 0;
        long[] segmentStarts = new long[1 << SEGMENT_BITS];
        int currentSegment = 0;
        long lastHash = 0;
        ArrayList<Long> keys = new ArrayList<Long>();
        while (true) {
            String line = lineReader.readLine();
            if (line == null) {
                break;
            }
            lines++;
            long hash = 0;
            for (int i = 0; i < 16; i++) {
                hash = (hash << 4) | StringUtils.getHex(line.charAt(i));
            }
            if (lastHash == hash) {
                System.out.println("Warning: duplicate 64-bit key detected, ignoring: " + line);
                continue;
            } else if (Long.compareUnsigned(hash, lastHash) < 0) {
                throw new IllegalArgumentException("The file is not sorted by hash");
            }
            lastHash = hash;
            int dot = line.lastIndexOf(':');
            int count = Integer.parseInt(line.substring(dot + 1), 10);
            // clear the lowest bit
            long key = hash ^ (hash & 1);
            // if common, set the lowest bit
            if (count > 9) {
                key |= 1;
            }
            int segment = (int) (key >>> (64 - SEGMENT_BITS));
            if (segment != currentSegment) {
                segmentStarts[currentSegment] = outPos;
                byte[] data = getSegment(keys);
                out.write(data);
                outPos += data.length;
                keys.clear();
                currentSegment = segment;
            }
            if (lines % 10000000 == 0) {
                long time = System.nanoTime() - start;
                System.out.println(lines / 1000000 + " million lines processed, " + (time / lines) + " ns/line");
            }
            keys.add(key);
        }
        segmentStarts[currentSegment] = outPos;
        out.write(getSegment(keys));
        lineReader.close();
        for (long s : segmentStarts) {
            out.writeLong(s);
        }
        out.close();
        long time = System.nanoTime() - start;
        System.out.println(lines + " lines processed, " + (time / 1000000 / 1000) + " seconds");
    }

    private static byte[] getSegment(ArrayList<Long> keys) {
        long[] array = new long[keys.size()];
        for (int i = 0; i < keys.size(); i++) {
            array[i] = keys.get(i);
        }
        return XorPlus8.construct(array).getData();
    }

}
