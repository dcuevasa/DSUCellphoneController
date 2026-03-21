# Luma3DS Input Redirection Protocol (formal definition)

## 1. Scope

This document defines the network protocol used by Rosalina (Luma3DS) for InputRedirection.
It is based on behavior implemented in Luma3DS (`sysmodules/rosalina/source/input_redirection.c` and `input_redirection_hooks.s`) and on the reference Qt Android client.

Normative keywords used in this document are: MUST, MUST NOT, SHOULD, MAY.

## 2. Transport and session model

- Transport protocol: UDP over IPv4.
- Server side: Nintendo 3DS (Rosalina InputRedirection thread).
- Client side: external sender (PC/Android/etc.).
- Server bind endpoint: `<3DS_IP>:4950/udp`.
- Connection model: stateless datagrams. No handshake, no ack, no retransmission, no keepalive signaling at protocol level.
- Security model: none (no auth, no encryption, no integrity checksum in payload).

## 3. Datagram sizes

The receiver behavior is:

- Datagrams with length `< 12` bytes: ignored.
- Datagrams with length `>= 12` and `< 20`: accepted as "legacy" frame (HID + touch + circle only).
- Datagrams with length `>= 20`: accepted as full frame. Bytes after offset 19 are ignored.

Therefore, clients SHOULD send exactly 20 bytes.

## 4. Endianness and primitive types

- Integer type for all fields: unsigned 32-bit (`u32`).
- Endianness on wire: little-endian.

## 5. Frame layout (20-byte frame)

| Offset | Size | Name | Type | Description |
|---:|---:|---|---|---|
| 0x00 | 4 | `hid_pad` | `u32` | Digital buttons (A/B/Start/etc.) |
| 0x04 | 4 | `touch` | `u32` | Touchscreen packed value |
| 0x08 | 4 | `circle_pad` | `u32` | Circle Pad packed value |
| 0x0C | 4 | `cpp_ir` | `u32` | C-Stick + ZL/ZR packed value |
| 0x10 | 4 | `special` | `u32` | HOME/POWER synthetic events |

Reference C struct (wire order):

```c
typedef struct {
		uint32_t hid_pad;    // LE
		uint32_t touch;      // LE
		uint32_t circle_pad; // LE
		uint32_t cpp_ir;     // LE
		uint32_t special;    // LE
} ir_frame_v1;
```

## 6. Neutral/sentinel values

These values are interpreted specially by Luma patches:

- `hid_pad = 0x00000FFF`: "no remote HID override".
- `touch = 0x02000000`: "no remote touch override".
- `circle_pad = 0x007FF7FF`: "no remote Circle Pad override".
- `cpp_ir = 0x80800081`: neutral C-Stick + no ZL/ZR.
- `special = 0x00000000`: no special key action.

If a field equals its sentinel, local physical input is passed through for that source.

## 7. Field-level formal semantics

### 7.1 `hid_pad` (offset 0)

12 low bits are used. Bits are active-low (0 = pressed, 1 = released).

| Bit | Button |
|---:|---|
| 0 | A |
| 1 | B |
| 2 | Select |
| 3 | Start |
| 4 | DPad Right |
| 5 | DPad Left |
| 6 | DPad Up |
| 7 | DPad Down |
| 8 | R |
| 9 | L |
| 10 | X |
| 11 | Y |

Unused upper bits SHOULD be set to 0.

### 7.2 `touch` (offset 4)

This is a raw packed touch register value expected by the HID path.

De-facto encoding used by clients:

- No touch: `0x02000000`.
- Touch active: `(1 << 24) | ((y & 0xFFF) << 12) | (x & 0xFFF)`.

Where:

- `x` is 12-bit touchscreen X.
- `y` is 12-bit touchscreen Y.

Important:

- This protocol path carries one touch point only.
- Multitouch is not represented.

### 7.3 `circle_pad` (offset 8)

Packed as:

- `circle_pad = ((y & 0xFFF) << 12) | (x & 0xFFF)`.

Common center value used by clients when actively overriding is around `x=0x800`, `y=0x800`.
Sentinel `0x007FF7FF` means "do not override local Circle Pad".

### 7.4 `cpp_ir` (offset 12)

Packed as:

- Bits 31..24: C-Stick Y (8-bit)
- Bits 23..16: C-Stick X (8-bit)
- Bits 15..8: IR button bits (ZL/ZR)
- Bits 7..0: constant `0x81`

IR button bits commonly used:

- bit 1 => ZR
- bit 2 => ZL

Neutral value: `0x80800081` (X=0x80, Y=0x80, no ZL/ZR).

### 7.5 `special` (offset 16)

`special` bits are edge-triggered into system events by Rosalina:

- bit 0: HOME state
	- rising edge: publishes HOME pressed event
	- falling edge: publishes HOME released event
- bit 1: POWER short press (rising edge only)
- bit 2: POWER long hold (rising edge only)

Bits above bit 2 are currently ignored by the implementation.

## 8. Receiver algorithm (normative)

For each received UDP datagram:

1. If length `< 12`, discard.
2. Copy bytes `0..11` into internal remote input state (`hid_pad`, `touch`, `circle_pad`).
3. If length `>= 20`:
	 - copy bytes `12..15` into `cpp_ir`;
	 - copy bytes `16..19` into `special` and emit edge-based events.
4. During HID/IR processing, each local source is replaced by remote value only if that remote value is not the source sentinel.

## 9. Timing and recommended send rate

The protocol does not define a hard frame rate. A practical client rate is 50 Hz (every 20 ms), which is what the reference Qt client uses.

Because UDP is unreliable, clients SHOULD continue sending current state periodically, not only on change.

## 10. Compatibility notes

- `12-byte` senders are accepted but cannot control C-Stick/ZL/ZR/HOME/POWER.
- `20-byte` senders are fully featured for current implementation.

## 11. Is touchscreen control possible?

Yes.

Touchscreen control is explicitly supported by the protocol through the `touch` field at offset `0x04`.

To press at `(x, y)`, send:

```c
touch = (1u << 24) | ((y & 0xFFF) << 12) | (x & 0xFFF);
```

To release touch, send:

```c
touch = 0x02000000;
```

Constraints:

- single touch point only;
- no pressure or multi-contact semantics.

## 12. Minimal full-frame example

Example logical state:

- A pressed
- no touch
- Circle Pad neutral override (center)
- C-Stick neutral
- no special buttons

Fields:

- `hid_pad = 0x00000FFE` (bit 0 cleared => A pressed)
- `touch = 0x02000000`
- `circle_pad = 0x00800800`
- `cpp_ir = 0x80800081`
- `special = 0x00000000`

Serialized as 20 LE bytes in that exact field order.
