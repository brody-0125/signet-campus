# Public Suffix List source

`public_suffix_list.dat` is the unmodified source used by OkHttp 4.12.0 for its embedded `publicsuffixes.gz` rules. It is covered by the [Mozilla Public License 2.0](../../LICENSES/MPL-2.0.txt), including the original copyright and notices in the file.

- [Immutable upstream source](https://github.com/square/okhttp/blob/40cb04338da423f7f73e83025717652ed35b7896/okhttp/src/test/resources/okhttp3/internal/publicsuffix/public_suffix_list.dat)
- SHA-256: `e8b273972eb5a70e888bd3e7d7c5b9b04e600a59d69def9136a74d70ae6fcdd3`
- [Upstream generator](https://github.com/square/okhttp/blob/parent-4.12.0/okhttp/src/test/java/okhttp3/internal/publicsuffix/PublicSuffixListGenerator.java)

The server JAR includes this source and the MPL license under `META-INF`. The `verifyDistributionNotices` Gradle task verifies the source hash and reproduces both rule arrays from the actual nested OkHttp JAR. Source bytes are preserved across checkouts; no changes have been made to the covered file.
