package org.fastfilter.xor;

import java.util.Random;

import org.fastfilter.utils.Hash;
import org.fastfilter.utils.RandomGenerator;

public class ProbabilityOfMappingStep {

    private static final Random random = new Random(1);
    private static final int HASHES = 3;

    public static void main(String... args) {
        int seed = 0;
        int testCount = 1000;
        System.out.println("arraySize s p");
        for(int add = 16; add <= 64; add *= 2) {
            System.out.println("\\addplot table {");
            for (int size = 3; size < 16 * 65536; size *= 3) {
                long[] list = new long[size];
                int arrayLength = (int) ((size * 1.23) + add) / 3 * 3;
                int totalRetries = 0;
                for(int test = 0; test < testCount; test++) {
                    random(list, seed);
                    seed += list.length;
                    int retryCount = getRetryCount(list, arrayLength, 0);
                    totalRetries += retryCount;
                }
                double p = (double) (testCount - totalRetries) / testCount;
                System.out.println(size + " " + p);
            }
            System.out.println("};");
            System.out.println("\\addlegendentry{$1.23 \\cdot size + "+add+"$}");
        }
    }

    static void random(long[] list, int seed) {
        RandomGenerator.createRandomUniqueListFast(list, seed);
//        for(int i=0; i<list.length; i++) {
//            list[i] = random.nextLong();
//        }
    }

    public static void mainAdd(String... args) {
        int seed = 0;
        for (int size = 1; size < 10000000; size *= 2) {
            long[] list = new long[size];
            for (int add = 2;; add *= 2) {
                int totalRetries = 0;
                int arrayLength = 0;
                for (int test = 0; test < 1000; test++) {
                    RandomGenerator.createRandomUniqueListFast(list, seed);
                    seed += list.length;
                    arrayLength = (int) ((size * 1.23) + add) / 3 * 3;
                    int retryCount = getRetryCount(list, arrayLength, 0);
                    totalRetries += retryCount;
                    if (totalRetries > 0) {
                        break;
                    }
                }
                // System.out.println("    size " + size + " add " + add + " totalRetries " + totalRetries);
                if (totalRetries == 0) {
                    System.out.println("size " + size + ": " + ((double) arrayLength / size) + " * size = arrayLength (1.23 * size + " + add + ")");
                    if (add == 2 && size > 1000) {
                        System.out.println("done");
                        return;
                    }
                    break;
                }
            }
        }
    }

    public static int getRetryCount(long[] keys, int arrayLength, int maxRetries) {
        int size = keys.length;
        int blockLength = arrayLength / HASHES;
        int m = arrayLength;
        int retryCount = 0;
        while (true) {
            int seed = random.nextInt();
            byte[] t2count = new byte[m];
            long[] t2 = new long[m];
            for (long k : keys) {
                for (int hi = 0; hi < HASHES; hi++) {
                    int h = getHash(k, seed, hi, blockLength);
                    t2[h] ^= k;
                    if (t2count[h] > 120) {
                        // probably something wrong with the hash function
                        throw new IllegalArgumentException();
                    }
                    t2count[h]++;
                }
            }
            int reverseOrderPos = 0;
            int[][] alone = new int[HASHES][blockLength];
            int[] alonePos = new int[HASHES];
            for (int nextAlone = 0; nextAlone < HASHES; nextAlone++) {
                for (int i = 0; i < blockLength; i++) {
                    if (t2count[nextAlone * blockLength + i] == 1) {
                        alone[nextAlone][alonePos[nextAlone]++] = nextAlone * blockLength + i;
                    }
                }
            }
            int found = -1;
            while (true) {
                int i = -1;
                for (int hi = 0; hi < HASHES; hi++) {
                    if (alonePos[hi] > 0) {
                        i = alone[hi][--alonePos[hi]];
                        found = hi;
                        break;
                    }
                }
                if (i == -1) {
                    // no entry found
                    break;
                }
                if (t2count[i] <= 0) {
                    continue;
                }
                long k = t2[i];
                if (t2count[i] != 1) {
                    throw new AssertionError();
                }
                --t2count[i];
                // which index (0, 1, 2) the entry was found
                for (int hi = 0; hi < HASHES; hi++) {
                    if (hi != found) {
                        int h = getHash(k, seed, hi, blockLength);
                        int newCount = --t2count[h];
                        if (newCount == 1) {
                            // we found a key that is _now_ alone
                            alone[hi][alonePos[hi]++] = h;
                        }
                        // remove this key from the t2 table, using xor
                        t2[h] ^= k;
                    }
                }
                reverseOrderPos++;
            }
            // this means there was no cycle
            if (reverseOrderPos == size) {
                break;
            }
            retryCount++;
            if (retryCount > maxRetries) {
                break;
            }
        }
        return retryCount;
    }

    private static int getHash(long key, long seed, int index, int blockLength) {
        long hash = Hash.hash64(key, seed);
        int r;
        switch (index) {
        case 0:
            r = (int) (hash);
            break;
        case 1:
            r = (int) Long.rotateLeft(hash, 21);
            break;
        default:
            r = (int) Long.rotateLeft(hash, 42);
            break;
        }
        r = Hash.reduce((int) r, blockLength);
        r = r + index * blockLength;
        return (int) r;
    }

}
