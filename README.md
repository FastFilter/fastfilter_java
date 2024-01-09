# Fast Approximate Membership Filters in Java

[![Java 17 CI](https://github.com/FastFilter/fastfilter_java/actions/workflows/java.yml/badge.svg)](https://github.com/FastFilter/fastfilter_java/actions/workflows/java.yml)

The following filter types are currently implemented:

* Xor filter: 8 and 16 bit variants; needs less space than cuckoo filters, with faster lookup
* Xor+ filter: 8 and 16 bit variants; compressed xor filter
* Xor binary fuse filter: 8 bit variant; needs less space than xor filters, with faster lookup
* Cuckoo filter: 8 and 16 bit variants; uses cuckoo hashing to store fingerprints
* Cuckoo+ filter: 8 and 16 bit variants, need a bit less space than regular cuckoo filters
* Bloom filter: the 'standard' algorithm
* Blocked Bloom filter: faster than regular Bloom filters, but need a bit more space
* Counting Bloom filter: allow removing entries, but need 4 times more space
* Succinct counting Bloom filter: about half the space of regular counting Bloom filters; faster lookup but slower add / remove
* Succinct counting blocked Bloom filter: same lookup speed as blocked Bloom filter

The following additional types are implemented, but less tested:

* Golomb Compressed Set (GCS): needs less space than cuckoo filters, but lookup is slow
* Minimal Perfect Hash filter: needs less space than cuckoo filters, but lookup is slow

## Reference

* Thomas Mueller Graf, Daniel Lemire, [Binary Fuse Filters: Fast and Smaller Than Xor Filters](http://arxiv.org/abs/2201.01174), Journal of Experimental Algorithmics (to appear). DOI: 10.1145/3510449   
* Thomas Mueller Graf,  Daniel Lemire, [Xor Filters: Faster and Smaller Than Bloom and Cuckoo Filters](https://arxiv.org/abs/1912.08258), Journal of Experimental Algorithmics 25 (1), 2020. DOI: 10.1145/3376122

## Usage

When using Maven:

    <dependency>
      <groupId>io.github.fastfilter</groupId>
      <artifactId>fastfilter</artifactId>
      <version>1.0.2</version>
    </dependency>

# Other Xor Filter Implementations

* [C](https://github.com/FastFilter/xor_singleheader)
* [C99](https://github.com/skeeto/xf8)
* [C++](https://github.com/FastFilter/fastfilter_cpp)
* [Erlang](https://github.com/mpope9/exor_filter)
* [Go](https://github.com/FastFilter/xorfilter)
* [Java](https://github.com/komiya-atsushi/xor-filter)
* [Python](https://github.com/GreyDireWolf/pyxorfilter)
* Rust: [1](https://github.com/bnclabs/xorfilter), [2](https://github.com/codri/xorfilter-rs), [3](https://github.com/Polochon-street/rustxorfilter)
* [C#](https://github.com/jonmat/FastIndex)

## Password Lookup Tool

Included is a tool to build a filter from a list of known password (hashes), and a tool to do lookups. That way, the password list can be queried locally, without requiring a large file. The filter is only 650 MB, instead of the original file which is 11 GB. At the cost of some false positives (unknown passwords reported as known, with about 1% probability).

### Generate the Password Filter File

Download the latest SHA-1 password file that is ordered by hash,
for example the file pwned-passwords-sha1-ordered-by-hash-v4.7z (~10 GB)
from https://haveibeenpwned.com/passwords
with about 550 million passwords.

If you have enough disk space, you can extract the hash file (~25 GB),
and convert it as follows:

    mvn clean install
    cat hash.txt | java -cp target/fastfilter*.jar org.fastfilter.tools.BuildFilterFile filter.bin

Converting takes about 2-3 minutes (depending on hardware).
To save disk space, you can extract the file on the fly (Mac OS X using Keka):

    /Applications/Keka.app/Contents/Resources/keka7z e -so
        pass.7z | java -cp target/fastfilter*.jar org.fastfilter.tools.BuildFilterFile filter.bin

Both will generate a file named filter.bin (~630 MB).

### Check Passwords

    java -cp target/fastfilter*.jar org.fastfilter.tools.PasswordLookup filter.bin

Enter a password to see if it's in the list.
If yes, it will (for sure) either show "Found", or "Found; common",
which means it was seen 10 times or more often.
Passwords not in the list will show "Not found" with more than 99% probability,
and with less than 1% probability "Found" or "Found; common".

Internally, the tool uses a xor+ filter (see above) with 8 bits per fingerprint. Actually, 1024 smaller filters (segments) are made, the segment id being the highest 10 bits of the key. The lowest bit of the key is set to either 0 (regular) or 1 (common), and so two lookups are made per password. Because of that, the false positive rate is twice of what it would be with just one lookup (0.0078 instead of 0.0039). A regular Bloom filter with the same guarantees would be ~760 MB. For each lookup, one filter segment (so, less than 1 MB) are read from the file.



