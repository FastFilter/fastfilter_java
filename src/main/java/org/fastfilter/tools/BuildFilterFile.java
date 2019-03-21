package org.fastfilter.tools;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.util.ArrayList;

import org.fastfilter.utils.StringUtils;
import org.fastfilter.xorplus.XorPlus8;

public class BuildFilterFile {

    public static final int SEGMENT_BITS = 4;

    public static void main(String... args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: java " + BuildFilterFile.class.getName() + " <textFile>\n"
                    + "Builds a .filter file from a text file that contains SHA-1 hashes and counts.");
            // see also https://haveibeenpwned.com/passwords
            return;
        }
        String textFile = args[0];
        String filterFileName = textFile + ".filter";
        long start = System.nanoTime();
        LineNumberReader lineReader = new LineNumberReader(new InputStreamReader(
                new BufferedInputStream(new FileInputStream(textFile)), Charset.forName("LATIN1")));
        new File(filterFileName).delete();
        RandomAccessFile out = new RandomAccessFile(filterFileName, "rw");
        int lines = 0;
        // header
        out.write(new byte[8 << SEGMENT_BITS]);
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
                hash <<= 4;
                hash |= StringUtils.getHex(line.charAt(i));
            }
            if (lastHash == hash) {
                System.out.println("Warning: duplicate hash detected, ignoring: " + line);
                continue;
            }
            lastHash = hash;
            int dot = line.lastIndexOf(':');
            int count = Integer.parseInt(line.substring(dot + 1), 10);
            // set the lowest bit to 0
            long key = hash ^ (hash & 1);
            // if common, set the lowest bit
            if (count > 9) {
                key |= 1;
            }
            int segment = (int) (key >>> (64 - SEGMENT_BITS));
            if (segment != currentSegment) {
                writeSegment(keys, currentSegment, out);
                long time = System.nanoTime() - start;
                System.out.println("Lines processed: " + lines + " " + (time / lines) + " ns/line");
                currentSegment = segment;
            }
            keys.add(key);
        }
        writeSegment(keys, currentSegment, out);
        lineReader.close();
        out.close();
    }

    private static void writeSegment(ArrayList<Long> keys, int segment,
            RandomAccessFile out) throws IOException {
        long[] array = new long[keys.size()];
        for(int i=0; i<keys.size(); i++) {
            array[i] = keys.get(i);
        }
        long start = out.length();
        out.seek(segment * 8);
        out.writeLong(start);
        out.seek(start);
        XorPlus8 filter = XorPlus8.construct(array);
        out.write(filter.getData());
        keys.clear();
    }

}
