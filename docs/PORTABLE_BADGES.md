# Portable badge images

After issuing a badge, choose **Download PNG** or **Download SVG** beside its JSON download. Both individual achievements and pathway completion awards use the same export flow. The original blue medal artwork is part of Signet Campus and covered by its MIT license.

Every image embeds the stored signed Open Badges credential unchanged. Anyone receiving the file can extract its badge details and recipient identifier. Downloading does not publish the credential or grant public access to your account, evidence submissions or other awards. Keep the JSON or image file wherever you keep personal credentials.

The PNG uses a 512 × 512 transparent image and an uncompressed `iTXt` entry with keyword `openbadgecredential`. The SVG uses the `https://purl.imsglobal.org/ob/v3p0` namespace and an `openbadges:credential` element containing the signed JSON in CDATA, without a JWT `verify` attribute. These formats follow [Open Badges 3.0 baking](https://www.imsglobal.org/spec/ob/v3p0/#baked-badge), implemented through the published Signet starter.

An image is a portable credential container, not a statement that its credential is currently valid. Expired or revoked awards remain downloadable with their original signatures. Extract and verify the credential, including its issuer and revocation status, before relying on it. Copying, resizing or processing an image through a platform that removes metadata may remove the embedded credential; retain the original download.

## API

`GET /api/credentials/{id}/image/{format}` accepts `png` or `svg`. Authentication is required and only the credential owner may download. Responses use `Cache-Control: no-store`, the corresponding image media type, and attachment filename `signet-campus-{id}.{format}`. Unsupported formats return 400; absent or inaccessible credentials return 404. The service renders its own fixed artwork and does not accept remote URLs or uploaded source images.

Private image export does not create a public sharing link. Share the file only with the recipients you choose. Public sharing controls and account transfer are separate capabilities.
