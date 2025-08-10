# ============================================
# flake.nix - FastFilter Java C++ FFI Development Environment
# ============================================

{
  description = "FastFilter Java C++ FFI Development Environment with LibC Detection, QEMU Testing, and .env Support";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";

    # Cross-compilation support
    nixpkgs-cross.url = "github:NixOS/nixpkgs/nixos-unstable";

    # Alternative LibC implementations
    musl-cross.url = "github:NixOS/nixpkgs/nixos-unstable";
  };

  outputs = { self, nixpkgs, flake-utils, nixpkgs-cross, musl-cross }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs = nixpkgs.legacyPackages.${system};

        # Cross-compilation targets
        crossSystems = {
          aarch64 = pkgs.pkgsCross.aarch64-multiplatform;
          armv7l = pkgs.pkgsCross.armv7l-hf-multiplatform;
          riscv64 = pkgs.pkgsCross.riscv64;
          s390x = pkgs.pkgsCross.s390x;
          mips64 = pkgs.pkgsCross.mips64el-linux-gnuabi64;
          powerpc64 = pkgs.pkgsCross.powernv;
          wasm32 = pkgs.pkgsCross.wasi32;
        };

        # Musl-based systems
        muslSystems = {
          x86_64-musl = pkgs.pkgsMusl;
          aarch64-musl = pkgs.pkgsCross.aarch64-multiplatform.pkgsMusl;
        };

        # Java package with FFM support (JDK 24 preferred, fallback to 21)
        jdk = pkgs.openjdk24 or pkgs.openjdk21;

        # QEMU configurations for different architectures
        qemuConfigs = {
          x86_64 = {
            qemu = pkgs.qemu;
            arch = "x86_64";
            cpu = "max";
            machine = "q35";
            memory = "2G";
          };

          aarch64 = {
            qemu = pkgs.qemu;
            arch = "aarch64";
            cpu = "cortex-a72";
            machine = "virt";
            memory = "2G";
          };

          armv7l = {
            qemu = pkgs.qemu;
            arch = "arm";
            cpu = "cortex-a15";
            machine = "virt";
            memory = "1G";
          };

          riscv64 = {
            qemu = pkgs.qemu;
            arch = "riscv64";
            cpu = "rv64";
            machine = "virt";
            memory = "2G";
          };

          s390x = {
            qemu = pkgs.qemu;
            arch = "s390x";
            cpu = "max";
            machine = "s390-ccw-virtio";
            memory = "2G";
          };

          powerpc64 = {
            qemu = pkgs.qemu;
            arch = "ppc64";
            cpu = "power9";
            machine = "pseries";
            memory = "2G";
          };
        };

        # Test environments with different LibCs
        testEnvironments = {
          # Alpine Linux with musl
          alpine-musl = pkgs.dockerTools.pullImage {
            imageName = "alpine";
            imageDigest = "sha256:51b67269f354137895d43f3b3d810bfacd3945438e94dc5ac55fdac340352f48";
            sha256 = "sha256-0000000000000000000000000000000000000000000=";
            finalImageTag = "latest";
          };

          # Ubuntu with glibc
          ubuntu-glibc = pkgs.dockerTools.pullImage {
            imageName = "ubuntu";
            imageDigest = "sha256:2b7412e6465c3c7fc5bb21d3e6f1917c167358449f2fa25f3e742440c8c91d89";
            sha256 = "sha256-0000000000000000000000000000000000000000000=";
            finalImageTag = "22.04";
          };

          # Void Linux with musl option
          void-musl = pkgs.dockerTools.pullImage {
            imageName = "voidlinux/voidlinux-musl";
            imageDigest = "sha256:abcdef1234567890";
            sha256 = "sha256-0000000000000000000000000000000000000000000=";
            finalImageTag = "latest";
          };
        };

        # Build test runner script
        testRunner = pkgs.writeScriptBin "run-libc-tests" ''
          #!${pkgs.bash}/bin/bash
          set -e

          echo "LibC Detection QEMU Testing Environment"
          echo "======================================="

          # Colors for output
          RED='\033[0;31m'
          GREEN='\033[0;32m'
          YELLOW='\033[1;33m'
          NC='\033[0m' # No Color

          # Test function
          run_test() {
            local name=$1
            local qemu_cmd=$2
            local test_cmd=$3

            echo -e "''${YELLOW}Testing: $name''${NC}"

            if $qemu_cmd -append "$test_cmd" >/tmp/qemu_$name.log 2>&1; then
              echo -e "''${GREEN}✓ $name passed''${NC}"
              return 0
            else
              echo -e "''${RED}✗ $name failed''${NC}"
              cat /tmp/qemu_$name.log
              return 1
            fi
          }

          # Run tests for each architecture
          echo "Running architecture tests..."

          ${pkgs.lib.concatStringsSep "\n" (pkgs.lib.mapAttrsToList (arch: config: ''
            echo "Testing ${arch}..."
            ${config.qemu}/bin/qemu-system-${config.arch} \
              -machine ${config.machine} \
              -cpu ${config.cpu} \
              -m ${config.memory} \
              -nographic \
              -kernel /path/to/kernel \
              -append "console=ttyS0" \
              || echo "Note: ${arch} requires kernel image"
          '') qemuConfigs)}

          echo "Tests complete!"
        '';

        # LibC detection test suite
        libcDetector = pkgs.stdenv.mkDerivation {
          pname = "libc-detector";
          version = "1.0.0";

          src = ./src;

          buildInputs = [ jdk ];

          buildPhase = ''
            javac -d . --enable-preview --release ${if pkgs ? openjdk24 then "24" else "21"} LibCType.java
          '';

          installPhase = ''
            mkdir -p $out/bin $out/lib
            cp -r com $out/lib/

            cat > $out/bin/detect-libc <<EOF
            #!/bin/sh
            exec ${jdk}/bin/java --enable-preview -cp $out/lib DetectLibC "\$@"
            EOF

            chmod +x $out/bin/detect-libc
          '';
        };

        # Cross-compilation test builder
        crossBuilder = arch: pkgs: pkgs.stdenv.mkDerivation {
          pname = "libc-test-${arch}";
          version = "1.0.0";

          src = ./src;

          buildInputs = [ pkgs.gcc ];

          buildPhase = ''
            ${pkgs.gcc}/bin/gcc -o libc-test test-libc.c
          '';

          installPhase = ''
            mkdir -p $out/bin
            cp libc-test $out/bin/
          '';
        };

        # VM test configurations
        vmTests = {
          alpine-x86_64 = {
            system = "x86_64-linux";
            libc = "musl";
            test = ''
              ldd --version 2>&1 | grep -i musl && echo "PASS: musl detected" || echo "FAIL: musl not detected"
            '';
          };

          ubuntu-x86_64 = {
            system = "x86_64-linux";
            libc = "glibc";
            test = ''
              ldd --version | grep -i glibc && echo "PASS: glibc detected" || echo "FAIL: glibc not detected"
            '';
          };

          alpine-aarch64 = {
            system = "aarch64-linux";
            libc = "musl";
            test = ''
              ldd --version 2>&1 | grep -i musl && echo "PASS: musl detected" || echo "FAIL: musl not detected"
            '';
          };
        };

        # Docker/OCI container builder for testing
        containerTests = pkgs.writeScriptBin "test-containers" ''
          #!${pkgs.bash}/bin/bash

          echo "Testing LibC in containers..."

          # Test Alpine (musl)
          ${pkgs.docker}/bin/docker run --rm alpine:latest sh -c "ldd --version 2>&1 | grep -i musl"

          # Test Ubuntu (glibc)
          ${pkgs.docker}/bin/docker run --rm ubuntu:22.04 sh -c "ldd --version | head -1"

          # Test Debian (glibc)
          ${pkgs.docker}/bin/docker run --rm debian:bullseye sh -c "ldd --version | head -1"

          # Test with different architectures using QEMU
          ${pkgs.docker}/bin/docker run --rm --platform linux/arm64 alpine:latest sh -c "uname -m && ldd --version 2>&1"
          ${pkgs.docker}/bin/docker run --rm --platform linux/arm/v7 alpine:latest sh -c "uname -m && ldd --version 2>&1"
        '';

        # NixOS VM for testing
        testVM = nixpkgs.lib.nixosSystem {
          system = "x86_64-linux";
          modules = [
            ({ config, pkgs, ... }: {
              # VM configuration
              virtualisation = {
                cores = 2;
                memorySize = 2048;
                diskSize = 4096;

                # Enable QEMU guest
                qemu = {
                  options = [
                    "-cpu host"
                    "-enable-kvm"
                  ];
                };
              };

              # Install test dependencies
              environment.systemPackages = with pkgs; [
                jdk
                gcc
                glibc
                musl
                file
                ldd
                strace
                ltrace
              ];

              # Test script
              systemd.services.libc-test = {
                description = "LibC Detection Test";
                wantedBy = [ "multi-user.target" ];
                script = ''
                  echo "Testing LibC detection..."
                  ldd --version

                  # Run Java detection
                  ${libcDetector}/bin/detect-libc
                '';
              };
            })
          ];
        };

      in {
        # Development shell
        devShells.default = pkgs.mkShell {
          buildInputs = with pkgs; [
            # Core tools
            qemu
            qemu-utils

            # Java development
            jdk
            maven
            gradle

            # Build tools
            gcc
            cmake
            ninja
            pkg-config

            # LibC implementations
            glibc
            musl

            # Cross-compilation tools
            pkgsCross.aarch64-multiplatform.buildPackages.gcc
            pkgsCross.armv7l-hf-multiplatform.buildPackages.gcc
            pkgsCross.riscv64.buildPackages.gcc

            # Container tools
            docker
            podman
            buildah

            # Testing tools
            gdb
            valgrind
            strace
            ltrace

            # Utilities
            file
            readelf
            objdump
            nm
            ldd
            patchelf

            # Scripting
            bash
            python3
            perl
          ];

          # Environment variable loading from .env files
          env = {
            # Load .env files in order of precedence
            DOTENV_FILES = ''.env.${pkgs.stdenv.hostPlatform.system} .env'';
          };

          shellHook = ''
            echo "FastFilter Java C++ FFI Development Environment"
            echo "==============================================="
            
            # Load .env files if they exist
            for envfile in .env.${pkgs.stdenv.hostPlatform.system} .env; do
              if [ -f "$envfile" ]; then
                echo "Loading environment from $envfile"
                set -a  # Export all variables
                source "$envfile"
                set +a  # Stop exporting
              fi
            done
            
            echo ""
            echo "Available tools:"
            echo "  - QEMU for multiple architectures"
            echo "  - Cross-compilers for ARM, RISC-V, etc."
            echo "  - Docker/Podman for container testing"
            echo "  - Java ${jdk.version} with FFM support"
            echo ""
            echo "Environment:"
            echo "  - JDK: ${jdk}"
            echo "  - JAVA_HOME: ''${JAVA_HOME:-${jdk}/}"
            echo "  - CC: ''${CC:-gcc}"
            echo "  - CXX: ''${CXX:-g++}"
            echo ""
            echo "Run 'run-libc-tests' to start testing"
            echo "Run 'test-containers' for container tests"
            echo ""

            # Set up QEMU binfmt if not already configured
            if [ ! -e /proc/sys/fs/binfmt_misc/qemu-aarch64 ]; then
              echo "Setting up QEMU binfmt handlers..."
              sudo ${pkgs.qemu}/bin/qemu-binfmt-conf.sh --qemu-path ${pkgs.qemu}/bin
            fi

            # Aliases for convenience
            alias test-alpine-x64="qemu-system-x86_64 -m 2G -nographic -hda alpine-x64.qcow2"
            alias test-alpine-arm="qemu-system-aarch64 -m 2G -nographic -hda alpine-arm.qcow2"
            alias compile-all="javac --enable-preview --release ${if pkgs ? openjdk24 then "24" else "21"} -d build src/**/*.java"
            alias run-detector="java --enable-preview -cp build com.example.ffi.platform.LibCType"
          '';
        };

        # Packages
        packages = {
          default = libcDetector;
          test-runner = testRunner;
          container-tests = containerTests;

          # Cross-compiled versions
          detector-aarch64 = crossBuilder "aarch64" crossSystems.aarch64;
          detector-armv7l = crossBuilder "armv7l" crossSystems.armv7l;
          detector-riscv64 = crossBuilder "riscv64" crossSystems.riscv64;

          # Musl versions
          detector-musl-x64 = crossBuilder "x86_64-musl" muslSystems.x86_64-musl;
          detector-musl-arm = crossBuilder "aarch64-musl" muslSystems.aarch64-musl;
        };

        # Apps for nix run
        apps = {
          default = flake-utils.lib.mkApp {
            drv = testRunner;
          };

          detect = flake-utils.lib.mkApp {
            drv = libcDetector;
            exePath = "/bin/detect-libc";
          };

          containers = flake-utils.lib.mkApp {
            drv = containerTests;
          };
        };

        # NixOS configurations for testing
        nixosConfigurations = {
          test-vm-glibc = testVM;

          test-vm-musl = nixpkgs.lib.nixosSystem {
            system = "x86_64-linux";
            modules = [
              ({ config, pkgs, ... }: {
                # Use musl-based packages
                nixpkgs.config.packageOverrides = pkgs: {
                  stdenv = pkgs.stdenvMusl;
                };

                environment.systemPackages = with pkgs; [
                  pkgsMusl.hello
                  libcDetector
                ];
              })
            ];
          };
        };

        # Hydra CI jobs
        hydraJobs = {
          inherit (self.packages.${system})
            default
            detector-aarch64
            detector-armv7l
            detector-musl-x64;

          tests = {
            vm-glibc = testVM.config.system.build.vm;
          };
        };
      });
}

# ============================================
# shell.nix - Classic Nix Shell (non-flake)
# ============================================

{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = with pkgs; [
    # QEMU and emulation
    qemu
    qemu-utils

    # Java (JDK 24 preferred)
    (pkgs.openjdk24 or pkgs.openjdk21)
    maven

    # Compilers and tools
    gcc
    musl
    glibc

    # Cross-compilation
    pkgsCross.aarch64-multiplatform.buildPackages.gcc
    pkgsCross.armv7l-hf-multiplatform.buildPackages.gcc

    # Analysis tools
    file
    readelf
    ldd
    strace

    # Container tools
    docker
    podman
  ];

  shellHook = ''
    echo "LibC QEMU Testing Environment"
    echo "============================="
    echo "QEMU version: $(qemu-system-x86_64 --version | head -1)"
    echo "Java version: $(java --version | head -1)"
    echo ""
    echo "Available QEMU systems:"
    ls ${pkgs.qemu}/bin/qemu-system-* | xargs -n1 basename
    echo ""
    echo "To test different architectures:"
    echo "  qemu-system-aarch64 -M virt -cpu cortex-a72 ..."
    echo "  qemu-system-riscv64 -M virt ..."
    echo "  qemu-system-s390x -M s390-ccw-virtio ..."
  '';
}

# ============================================
# test-libc.c - Simple C test program
# ============================================

/*
#include <stdio.h>
#include <features.h>

int main() {
    printf("LibC Detection Test\n");
    printf("===================\n");

    #ifdef __GLIBC__
    printf("Detected: GNU libc (glibc)\n");
    printf("Version: %d.%d\n", __GLIBC__, __GLIBC_MINOR__);
    #endif

    #ifdef __MUSL__
    printf("Detected: musl libc\n");
    #endif

    #ifdef __UCLIBC__
    printf("Detected: uClibc\n");
    #endif

    #ifdef __BIONIC__
    printf("Detected: Bionic (Android)\n");
    #endif

    #ifdef __dietlibc__
    printf("Detected: diet libc\n");
    #endif

    #ifdef __NEWLIB__
    printf("Detected: newlib\n");
    #endif

    return 0;
}
*/

# ============================================
# .envrc - direnv configuration
# ============================================

# use flake

# ============================================
# Makefile - Build and test targets
# ============================================

/*
.PHONY: all test clean qemu-test docker-test

JAVAC = javac --enable-preview --release 21
JAVA = java --enable-preview

all: build

build:
	mkdir -p build
	$(JAVAC) -d build src/**/*.java

test: build
	$(JAVA) -cp build com.example.ffi.platform.LibCType

qemu-test-x86:
	qemu-system-x86_64 \
		-m 2G \
		-nographic \
		-kernel vmlinuz \
		-initrd initrd.img \
		-append "console=ttyS0"

qemu-test-arm:
	qemu-system-aarch64 \
		-M virt \
		-cpu cortex-a72 \
		-m 2G \
		-nographic \
		-kernel Image \
		-initrd initrd.img \
		-append "console=ttyAMA0"

docker-test:
	docker run --rm alpine:latest sh -c "ldd --version 2>&1"
	docker run --rm ubuntu:22.04 sh -c "ldd --version"
	docker run --rm --platform linux/arm64 alpine:latest sh -c "uname -m && ldd --version 2>&1"

clean:
	rm -rf build *.log

# Cross-compilation targets
cross-aarch64:
	aarch64-linux-gnu-gcc -o test-aarch64 test-libc.c

cross-armv7:
	arm-linux-gnueabihf-gcc -o test-armv7 test-libc.c

cross-riscv64:
	riscv64-linux-gnu-gcc -o test-riscv64 test-libc.c

# Run all cross-compiled tests with QEMU
qemu-user-test: cross-aarch64 cross-armv7 cross-riscv64
	qemu-aarch64-static ./test-aarch64
	qemu-arm-static ./test-armv7
	qemu-riscv64-static ./test-riscv64
*/