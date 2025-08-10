# FastFilter Java Deployment Guide

Complete guide for deploying FastFilter Java artifacts to GitHub Packages and Maven Central.

## Quick Start

### 1. Automated Setup
```bash
# Run the automated credential setup
./scripts/setup-github-credentials.sh

# Deploy a snapshot
./scripts/deploy-artifacts.sh snapshot

# Deploy a release  
./scripts/deploy-artifacts.sh release -v 1.0.3
```

### 2. Verify Deployment
```bash
# Check GitHub Packages
open "https://github.com/FastFilter/fastfilter_java/packages"

# Check artifacts
mvn dependency:get -DgroupId=io.github.fastfilter -DartifactId=fastfilter -Dversion=1.0.3-SNAPSHOT -DremoteRepositories=https://maven.pkg.github.com/FastFilter/fastfilter_java
```

## Repository Architecture

### GitHub Packages (Primary)
- **URL**: `https://maven.pkg.github.com/FastFilter/fastfilter_java`
- **Purpose**: Primary repository for snapshots and releases
- **Authentication**: GitHub token
- **Cost**: Free for public repositories

### Maven Central (Secondary)
- **URL**: `https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/`
- **Purpose**: Official releases for public consumption
- **Authentication**: Sonatype OSSRH account + GPG signing
- **Requirements**: Manual approval process

## Deployment Types

### Snapshot Deployment
**When**: Development builds, CI/CD
**Target**: GitHub Packages
**Automation**: Automatic on push to master
**Version**: Must end with `-SNAPSHOT`

```bash
# Manual snapshot deployment
./scripts/deploy-artifacts.sh snapshot
mvn clean deploy -Pgithub-packages-snapshot -Ddeploy.github.snapshot=true
```

### Release Deployment
**When**: Tagged releases
**Target**: GitHub Packages + Maven Central
**Automation**: Triggered by GitHub releases
**Version**: Non-SNAPSHOT versions

```bash
# Manual release deployment
./scripts/deploy-artifacts.sh release -v 1.0.3
mvn clean deploy -Pgithub-packages -Ddeploy.github=true
```

## Credential Management

### Automated Setup

The `setup-github-credentials.sh` script automates the entire credential setup process:

```bash
./scripts/setup-github-credentials.sh
```

**What it does**:
1. ✅ Authenticates GitHub CLI
2. ✅ Creates repository secrets
3. ✅ Configures Maven settings.xml
4. ✅ Generates local .env.local
5. ✅ Optional GPG setup for signing

### Manual Setup

#### GitHub CLI Authentication
```bash
# Install GitHub CLI
brew install gh  # macOS
sudo apt install gh  # Ubuntu

# Authenticate with required scopes
gh auth login --scopes "repo,packages:write,packages:read"

# Verify authentication
gh auth status
```

#### Repository Secrets
```bash
# Required secrets
gh secret set GITHUB_TOKEN --body "$(gh auth token)"
gh secret set MAVEN_USERNAME --body "$(gh api user --jq '.login')"
gh secret set MAVEN_PASSWORD --body "$(gh auth token)"

# Optional: GPG signing
gh secret set GPG_PASSPHRASE --body "your-gpg-passphrase"
gh secret set GPG_PRIVATE_KEY --body "$(gpg --armor --export-secret-keys KEY_ID)"

# Optional: Maven Central
gh secret set MAVEN_CENTRAL_USERNAME --body "your-sonatype-username"
gh secret set MAVEN_CENTRAL_PASSWORD --body "your-sonatype-password"
```

#### Maven Settings
```xml
<!-- ~/.m2/settings.xml -->
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_GITHUB_TOKEN</password>
    </server>
    
    <server>
      <id>ossrh</id>
      <username>${env.MAVEN_CENTRAL_USERNAME}</username>
      <password>${env.MAVEN_CENTRAL_PASSWORD}</password>
    </server>
  </servers>
</settings>
```

## Maven Profiles

### GitHub Packages Profiles

#### `github-packages` - Full Release
```bash
mvn clean deploy -Pgithub-packages -Ddeploy.github=true
```
- Builds sources and javadocs
- Deploys to GitHub Packages
- Used for release versions

#### `github-packages-snapshot` - Snapshot
```bash
mvn clean deploy -Pgithub-packages-snapshot -Ddeploy.github.snapshot=true
```
- Minimal deployment
- Only main JAR
- Used for SNAPSHOT versions

### Maven Central Profile

#### `maven-central` - Official Releases
```bash
mvn clean deploy -Pmaven-central -Ddeploy.central=true
```
- Full release build
- GPG signing required
- Sources and javadocs included
- Manual approval required in Nexus

## Automated Workflows

### PR Build Workflow (`pr-build.yml`)

**Snapshot Deployment Job**:
- **Trigger**: Push to master branch
- **Condition**: `!contains(github.event.head_commit.message, '[skip deploy]')`
- **Target**: GitHub Packages
- **Runs After**: All tests pass

```yaml
deploy-snapshot:
  if: github.ref == 'refs/heads/master' && github.event_name == 'push'
  needs: [build-and-test, integration-test]
  runs-on: ubuntu-latest
```

**Skip deployment**: Include `[skip deploy]` in commit message

### Release Workflow (`release.yml`)

**Release Deployment**:
- **Trigger**: GitHub release creation
- **Targets**: GitHub Packages + Maven Central
- **Features**: Multi-platform native libraries

```bash
# Create a release to trigger deployment
gh release create v1.0.3 --title "FastFilter Java v1.0.3" --generate-notes
```

## Version Management

### Semantic Versioning

FastFilter Java uses semantic versioning:
- **Major**: Breaking changes (e.g., 1.0.0 → 2.0.0)
- **Minor**: New features (e.g., 1.0.0 → 1.1.0)
- **Patch**: Bug fixes (e.g., 1.0.0 → 1.0.1)
- **Snapshot**: Development builds (e.g., 1.0.1-SNAPSHOT)

### Version Commands
```bash
# Set version manually
mvn versions:set -DnewVersion=1.0.3
mvn versions:commit

# Deploy with custom version
./scripts/deploy-artifacts.sh release -v 1.0.3

# Check current version
mvn help:evaluate -Dexpression=project.version -q -DforceStdout
```

## Using Deployed Artifacts

### GitHub Packages

Add to your project's `pom.xml`:

```xml
<repositories>
  <repository>
    <id>github</id>
    <name>GitHub FastFilter Packages</name>
    <url>https://maven.pkg.github.com/FastFilter/fastfilter_java</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>io.github.fastfilter</groupId>
    <artifactId>fastfilter</artifactId>
    <version>1.0.3-SNAPSHOT</version>
  </dependency>
</dependencies>
```

Configure authentication in `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_TOKEN</password>
  </server>
</servers>
```

### Maven Central

```xml
<dependencies>
  <dependency>
    <groupId>io.github.fastfilter</groupId>
    <artifactId>fastfilter</artifactId>
    <version>1.0.3</version>
  </dependency>
</dependencies>
```

No additional configuration needed - Maven Central is included by default.

## Troubleshooting

### Common Issues

#### 1. Authentication Failures
```bash
# Problem: 401 Unauthorized
# Solution: Verify GitHub token has packages:write scope
gh auth refresh --scopes "repo,packages:write,packages:read"

# Verify token permissions
gh api user/packages --paginate
```

#### 2. Version Conflicts
```bash
# Problem: Version already exists
# Solution: Use different version or delete existing
gh api -X DELETE /user/packages/maven/io.github.fastfilter.fastfilter/versions/VERSION_ID

# Check existing versions
gh api user/packages/maven/io.github.fastfilter.fastfilter/versions
```

#### 3. GPG Signing Issues
```bash
# Problem: GPG signing failed
# Solution: Verify GPG key and passphrase
gpg --list-secret-keys --keyid-format=long
echo "test" | gpg --clearsign --default-key KEY_ID
```

#### 4. Maven Settings
```bash
# Problem: Settings not found
# Solution: Verify settings.xml location
mvn help:effective-settings | grep settings

# Test repository access
mvn dependency:get -DgroupId=io.github.fastfilter -DartifactId=fastfilter -Dversion=1.0.3-SNAPSHOT
```

### Debug Commands

```bash
# Enable Maven debug output
mvn deploy -X -Pgithub-packages

# Check effective POM
mvn help:effective-pom -Pgithub-packages

# Validate repository access
curl -H "Authorization: token $(gh auth token)" https://maven.pkg.github.com/FastFilter/fastfilter_java/

# Check GitHub package permissions
gh api user/packages --paginate | jq '.[] | select(.name=="fastfilter")'
```

## Security Best Practices

### Token Security
- ✅ Use tokens with minimal required scopes
- ✅ Rotate tokens regularly
- ✅ Store tokens in GitHub secrets, not in code
- ✅ Use repository secrets, not environment variables in workflows

### GPG Signing
- ✅ Use strong GPG keys (4096-bit RSA)
- ✅ Set expiration dates on keys
- ✅ Upload public keys to key servers
- ✅ Backup private keys securely

### Access Control
- ✅ Limit repository access to necessary contributors
- ✅ Use branch protection rules
- ✅ Require PR reviews for releases
- ✅ Enable dependency scanning

## Monitoring and Maintenance

### Package Management
```bash
# List all packages
gh api user/packages --paginate

# View package details
gh api user/packages/maven/io.github.fastfilter.fastfilter

# Delete old versions (if needed)
gh api -X DELETE /user/packages/maven/io.github.fastfilter.fastfilter/versions/VERSION_ID
```

### Usage Analytics
- GitHub Packages provides download statistics
- Monitor package usage in repository insights
- Track issues related to specific versions

### Regular Maintenance
- 🔄 **Monthly**: Review and rotate access tokens
- 🔄 **Quarterly**: Clean up old SNAPSHOT versions
- 🔄 **Bi-annually**: Update GPG keys before expiration
- 🔄 **As needed**: Update Maven plugin versions

## Advanced Scenarios

### Multi-Module Deployments

FastFilter Java is a multi-module project. All modules are deployed together:

```bash
# Modules deployed:
# - io.github.fastfilter:fastfilter_java (parent)
# - io.github.fastfilter:fastfilter (core library)
# - io.github.fastfilter:jmh (benchmarks)
# - io.github.fastfilter:fastfilter_cpp_ffi (native integration)
```

### Cross-Platform Native Libraries

Release deployments include platform-specific native libraries:

```bash
# Generated artifacts:
# - fastfilter-native-linux-x86_64-VERSION.jar
# - fastfilter-native-linux-arm64-VERSION.jar  
# - fastfilter-native-macos-x86_64-VERSION.jar
# - fastfilter-native-macos-arm64-VERSION.jar
# - fastfilter-native-windows-x86_64-VERSION.jar
```

### Staging and Production

```bash
# Development workflow
git push origin master                    # → Auto-deploy SNAPSHOT
./scripts/deploy-artifacts.sh snapshot   # → Manual SNAPSHOT

# Release workflow  
mvn versions:set -DnewVersion=1.0.3      # → Set release version
git tag v1.0.3 && git push origin v1.0.3 # → Create tag
gh release create v1.0.3                 # → Trigger release deployment
```

This comprehensive deployment system ensures reliable, secure, and automated artifact distribution for the FastFilter Java project.