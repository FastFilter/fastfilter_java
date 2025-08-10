package org.fastfilter.ffi;


import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;

public class DetectionContext {
	private final Linker linker;
	private final SymbolLookup stdLibLookup;
	private String detectedVersion = "unknown";
	private int confidenceLevel = 0;

	public DetectionContext() {
		this.linker = Linker.nativeLinker();
		this.stdLibLookup = linker.defaultLookup();
	}

	public Linker getLinker() { return linker; }
	public SymbolLookup getStdLibLookup() { return stdLibLookup; }

	public String getDetectedVersion() { return detectedVersion; }
	public void setDetectedVersion(String version) { this.detectedVersion = version; }

	public int getConfidenceLevel() { return confidenceLevel; }
	public void setConfidenceLevel(int level) { this.confidenceLevel = level; }

	public void reset() {
		this.detectedVersion = "unknown";
		this.confidenceLevel = 0;
	}

	/**
	 * Execute a system command and return output
	 */
	public String executeCommand(String... command) throws Exception {
		ProcessBuilder pb = new ProcessBuilder(command);
		Process process = pb.start();

		StringBuilder output = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(process.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line).append("\n");
			}
		}

		process.waitFor();
		return output.toString();
	}
}

