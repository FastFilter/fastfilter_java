# Fast Approximate Membership Filters.

The following filter types are currently implemented:

* Bloom filter: the 'standard' algorithm
* Blocked Bloom filter: faster than regular Bloom filters, but need a bit more space
* Counting Bloom filter: allow removing entries, but need 4 times more space
* Succinct counting Bloom filter: about half the space of regular counting Bloom filters; faster lookup but slower add / remove
* Succinct counting blocked Bloom filter: same lookup speed as blocked Bloom filter
* Cuckoo filter: 8 and 16 bit variants; uses cuckoo hashing to store fingerprints
* Cuckoo+ filter: 8 and 16 bit variants, need a bit less space than regular cuckoo filters
* Golomb Compressed Set (GCS): needs less space than cuckoo filters, but lookup is slow
* Minimal Perfect Hash filter: needs less space than cuckoo filters, but lookup is slow
* Xor filter: 8 and 16 bit variants; needs less space than cuckoo filters, with faster lookup
* Xor+ filter: 8 and 16 bit variants; compressed xor filter

# Password Look Tool

Included is a tool to build a filter from a list of known password (hashes), and a tool to do lookups. That way, the password list can be queried locally, without requiring a large file. The filter is only 650 MB, instead of the original file which is 11 GB. At the cost of some false positives (unknown passwords reported as known, with about 1% probability).

## Generate the Password Filter File

Download the latest SHA-1 password file that is ordered by hash,
for example the file pwned-passwords-sha1-ordered-by-hash-v4.7z (10 GB)
from https://haveibeenpwned.com/passwords
with about 550 million passwords.

If you have enough disk space, you can extract the hash file (25 GB),
and convert it as follows:

    mvn clean install
    cat hash.txt | java -cp target/fastfilter*.jar org.fastfilter.tools.BuildFilterFile filter.bin

Converting takes about 2-3 minutes (depending on hardware).
To save disk space, you can extract the file on the fly (Mac OS X using Keka):

    /Applications/Keka.app/Contents/Resources/keka7z e -so
        pass.7z | java -cp target/fastfilter*.jar org.fastfilter.tools.BuildFilterFile filter.bin

Both will generate a file named filter.bin (640 MB).

## Check Passwords

    java -cp target/fastfilter*.jar org.fastfilter.tools.PasswordLookup filter.bin

Enter a password to see if it's in the list.
If yes, it will (for sure) either show "Found", or "Found; common",
which means it was seen 10 times or more often.
Passwords not in the list will show "Not found" with more than 99% probability,
and with less than 1% probability "Found" or "Found; common".
