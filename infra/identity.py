"""Initialize the two service identities inside their respective private volumes."""

from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa


def provision(private_file: Path, peer_public_file: Path) -> None:
    if private_file.exists():
        key = serialization.load_pem_private_key(private_file.read_bytes(), password=None)
        if not isinstance(key, rsa.RSAPrivateKey):
            raise ValueError("Expected an RSA signing key")
    else:
        if peer_public_file.exists():
            raise RuntimeError("Restore the missing identity volume before starting")
        key = rsa.generate_private_key(public_exponent=65537, key_size=3072)
        write_key(
            private_file,
            key.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.PKCS8,
                serialization.NoEncryption(),
            ),
        )
    public = key.public_key().public_bytes(
        serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
    )
    if peer_public_file.exists():
        if peer_public_file.read_bytes() != public:
            raise RuntimeError("Restore matching service identity volumes")
    else:
        write_key(peer_public_file, public)


def write_key(target: Path, content: bytes) -> None:
    # Only the intended service mounts this volume, read-only, under its own UID.
    pending = target.with_suffix(".pending")
    pending.write_bytes(content)
    pending.chmod(0o444)
    pending.replace(target)


if __name__ == "__main__":
    server = Path("/identity/server")
    ai = Path("/identity/ai")
    provision(server / "testopsai.workspace.java-private-key", ai / "TESTOPS_AI_SERVER_PUBLIC_KEY")
    provision(ai / "TESTOPS_AI_PRIVATE_KEY", server / "testopsai.workspace.ai-public-key")
