package org.fastfilter.ffi;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advanced platform detection utility for JDK 24 FFI integration
 * Provides comprehensive OS, architecture, and runtime environment detection
 */
public final class PlatformInfo
{

	// Singleton instance
	private static final PlatformInfo INSTANCE = new PlatformInfo();

	private final OSType os;
	private final CPUArch arch;
	private final String osVersion;
	private final String kernelVersion;
	private final boolean isMusl;
	private final boolean isAndroid;
	private final Map<String, String> environmentCache;

	private PlatformInfo() {
		this.os = detectOS();
		this.arch = detectArch();
		this.osVersion = System.getProperty("os.version", "unknown");
		this.kernelVersion = detectKernelVersion();
		this.isMusl = detectMusl();
		this.isAndroid = detectAndroid();
		this.environmentCache = new ConcurrentHashMap<>();
	}

	public static PlatformInfo getInstance() {
		return INSTANCE;
	}

	private static OSType detectOS() {
		String osName = System.getProperty("os.name", "").toLowerCase();

		if (osName.contains("win")) return OSType.WINDOWS;
		if (osName.contains("mac") || osName.contains("darwin")) return OSType.MACOS;
		if (osName.contains("linux")) return OSType.LINUX;
		if (osName.contains("freebsd")) return OSType.FREEBSD;
		if (osName.contains("openbsd")) return OSType.OPENBSD;
		if (osName.contains("sunos") || osName.contains("solaris")) return OSType.SOLARIS;
		if (osName.contains("aix")) return OSType.AIX;

		return OSType.UNKNOWN;
	}

	private static CPUArch detectArch() {
		String archName = System.getProperty("os.arch", "").toLowerCase();

		return Arrays.stream(CPUArch.values())
		             .filter(a -> a != CPUArch.UNKNOWN)
		             .filter(a -> a.matches(archName))
		             .findFirst()
		             .orElse(CPUArch.UNKNOWN);
	}

	private String detectKernelVersion() {
		if (os == OSType.LINUX || os == OSType.MACOS) {
			try {
				Process p = new ProcessBuilder("uname", "-r").start();
				try (Scanner s = new Scanner(p.getInputStream())) {
					return s.hasNextLine() ? s.nextLine().trim() : "unknown";
				}
			} catch (IOException e) {
				return "unknown";
			}
		}
		return osVersion;
	}

	private boolean detectMusl() {
		if (os != OSType.LINUX) return false;

		try {
			Process p = new ProcessBuilder("ldd", "--version").start();
			try (Scanner s = new Scanner(p.getInputStream())) {
				while (s.hasNextLine()) {
					if (s.nextLine().toLowerCase().contains("musl")) {
						return true;
					}
				}
			}
		} catch (IOException e) {
			// Check for Alpine Linux
			return Files.exists(Paths.get("/etc/alpine-release"));
		}
		return false;
	}

	private boolean detectAndroid() {
		return System.getProperty("java.runtime.name", "").toLowerCase().contains("android")
			       || System.getProperty("java.vendor", "").toLowerCase().contains("android")
			       || Files.exists(Paths.get("/system/build.prop"));
	}

	// Getters
	public OSType getOS() { return os; }
	public CPUArch getArch() { return arch; }
	public String getOSVersion() { return osVersion; }
	public String getKernelVersion() { return kernelVersion; }
	public boolean isMusl() { return isMusl; }
	public boolean isAndroid() { return isAndroid; }

	// Platform string generation
	public String canonicalPlatformName() {
		return String.format("%s-%s", os.getPlatformName(), arch.getName());
	}

	public String getDetailedPlatformString() {
		StringBuilder sb = new StringBuilder();
		sb.append(os.getName()).append("-").append(arch.getName());
		if (isAndroid) sb.append("-android");
		if (isMusl) sb.append("-musl");
		return sb.toString();
	}

	// CPU feature detection
	public Set<String> getCPUFeatures() {
		Set<String> features = new HashSet<>();

		if (os == OSType.LINUX) {
			try {
				List<String> lines = Files.readAllLines(Paths.get("/proc/cpuinfo"));
				Pattern flagsPattern = Pattern.compile("^flags\\s*:\\s*(.*)$");

				for (String line : lines) {
					Matcher m = flagsPattern.matcher(line);
					if (m.matches()) {
						features.addAll(Arrays.asList(m.group(1).split("\\s+")));
						break;
					}
				}
			} catch (IOException e) {
				// Ignore
			}
		} else if (os == OSType.MACOS) {
			try {
				Process p = new ProcessBuilder("sysctl", "-n", "hw.optional").start();
				try (Scanner s = new Scanner(p.getInputStream())) {
					while (s.hasNextLine()) {
						String line = s.nextLine();
						if (line.contains(": 1")) {
							features.add(line.split(":")[0].trim());
						}
					}
				}
			} catch (IOException e) {
				// Ignore
			}
		}

		return features;
	}

	// Check for specific CPU features
	public boolean hasAVX() { return getCPUFeatures().contains("avx"); }
	public boolean hasAVX2() { return getCPUFeatures().contains("avx2"); }
	public boolean hasAVX512() { return getCPUFeatures().stream().anyMatch(f -> f.startsWith("avx512")); }
	public boolean hasSSE4() { return getCPUFeatures().contains("sse4_2"); }
	public boolean hasNEON() { return arch == CPUArch.ARM64 || getCPUFeatures().contains("neon"); }
}