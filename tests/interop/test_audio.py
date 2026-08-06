"""`FIELD_AUDIO` (0x07) round-trip — codec_tag + bytes pair.

The audio fixture isn't real audio — `FIELD_AUDIO` interop is a
wire-format test only. Real audio decoding lives in `LXST` /
`Codec2`/`Opus`, exercised separately.
"""

from __future__ import annotations

import time
from pathlib import Path

import pytest

from verify import audio_payload


FIXTURES = Path(__file__).parent / "fixtures"

# Real upstream LXMF `AM_*` values (LXMF/LXMF.py). The previous 0x01 here was
# wrong twice over: it is AM_CODEC2_450PWB, a mode Sideband has no bindings for
# and refuses in `play_audio_field`, and it happened to pass only because both
# ends round-tripped the tag opaquely. Pinning the real values means the test
# fails if either side ever emits something a Sideband peer would reject.
AM_OPUS_OGG = 0x10
AM_CODEC2_1200 = 0x04    # LCS default for the low-bandwidth / LoRa profile
AM_CODEC2_2400 = 0x08    # Sideband's own PTT default when `hq_ptt` is off


@pytest.fixture(scope="session")
def audio_bytes_fixture() -> bytes:
    return (FIXTURES / "tone.opus").read_bytes()


@pytest.mark.timeout(90)
def test_audio_columba_to_sideband(interop, audio_bytes_fixture):
    text = f"audio_from_columba_{int(time.time() * 1000)}"
    interop.columba.send_audio(
        interop.sideband_hex,
        text=text,
        audio_bytes=audio_bytes_fixture,
        codec_tag=AM_OPUS_OGG,
    )

    msg = interop.sideband.wait_for_message(
        from_hex=interop.columba_hex,
        content_predicate=lambda m: m.content_text == text,
        timeout=60,
    )
    codec_tag, data = audio_payload(msg.fields)
    assert codec_tag == AM_OPUS_OGG
    assert data == audio_bytes_fixture


@pytest.mark.timeout(90)
def test_audio_sideband_to_columba(interop, audio_bytes_fixture):
    text = f"audio_from_sideband_{int(time.time() * 1000)}"
    assert interop.sideband.send_audio(
        interop.columba_hex,
        content=text,
        audio_bytes=audio_bytes_fixture,
        codec_tag=AM_OPUS_OGG,
    )

    msg = interop.columba.wait_for_message(
        from_hex=interop.sideband_hex,
        content_predicate=lambda m: m.content == text,
        timeout=60,
    )
    assert msg.content == text


@pytest.mark.timeout(90)
@pytest.mark.parametrize("mode", [AM_CODEC2_1200, AM_CODEC2_2400])
def test_audio_codec2_columba_to_sideband(interop, audio_bytes_fixture, mode):
    """Codec2 payloads are raw concatenated frames — no container, no header
    (`Sideband/sbapp/sideband/audioproc.py::encode_codec2`). The fixture is not
    real Codec2 data; this pins the mode byte and the payload's byte-identity
    across the wire, which is the whole interop surface. Decoding is covered by
    Codec2CodecTest on the Android side."""
    text = f"codec2_from_columba_{int(time.time() * 1000)}"
    interop.columba.send_audio(
        interop.sideband_hex,
        text=text,
        audio_bytes=audio_bytes_fixture,
        codec_tag=mode,
    )

    msg = interop.sideband.wait_for_message(
        from_hex=interop.columba_hex,
        content_predicate=lambda m: m.content_text == text,
        timeout=60,
    )
    codec_tag, data = audio_payload(msg.fields)
    assert codec_tag == mode
    assert data == audio_bytes_fixture


@pytest.mark.timeout(90)
def test_audio_codec2_sideband_to_columba(interop, audio_bytes_fixture):
    """Sideband's default PTT mode when `hq_ptt` is off (`main.py:2475`), so
    this is the most common inbound shape from a stock Sideband install."""
    text = f"codec2_from_sideband_{int(time.time() * 1000)}"
    assert interop.sideband.send_audio(
        interop.columba_hex,
        content=text,
        audio_bytes=audio_bytes_fixture,
        codec_tag=AM_CODEC2_2400,
    )

    msg = interop.columba.wait_for_message(
        from_hex=interop.sideband_hex,
        content_predicate=lambda m: m.content == text,
        timeout=60,
    )
    assert msg.content == text
