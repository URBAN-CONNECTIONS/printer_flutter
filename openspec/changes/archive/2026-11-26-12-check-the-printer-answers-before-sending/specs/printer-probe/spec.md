# Spec Delta

## Purpose

Lets a print call find out in about two seconds that the printer does not answer, before any byte of the job is sent to it.

## ADDED Requirements

### Requirement: Probe before the job of an atomic call
An atomic call SHALL, right after connecting, send the probe and wait for the answer for at most 2 seconds before sending any byte of the job. The probe SHALL be on by default and the caller SHALL be able to turn it off. No immediate command SHALL be relied upon.

#### Scenario: Atomic call probes first
- **WHEN** an atomic call connects to the printer
- **THEN** it sends the probe and waits for the answer for at most 2 seconds before sending the job

#### Scenario: Printer answers the probe
- **WHEN** the printer answers the probe
- **THEN** the job is sent as usual

#### Scenario: The default limit is 2 seconds
- **WHEN** an atomic call probes a printer that does not answer, with no unconfirmed job on it and the limit the plugin uses by default
- **THEN** the call fails after about 2 seconds, not after a longer limit such as 3 or 5 seconds

#### Scenario: Probe turned off
- **WHEN** an atomic call turns the probe off
- **THEN** no probe is sent and the job is sent right after connecting

### Requirement: A printer that does not answer fails fast
When the printer does not answer the probe within the limit, nothing of the job SHALL be sent, the connection SHALL be closed and the call SHALL fail with `PrinterErrorType.printerNotReady`, with a message saying the printer does not answer and must be turned off and on.

#### Scenario: No answer to the probe
- **WHEN** the printer does not answer the probe within the limit
- **THEN** nothing of the job is sent and the connection is closed
- **AND** the call fails with `PrinterErrorType.printerNotReady`, with a message saying the printer does not answer and must be turned off and on

#### Scenario: A stuck printer takes the probe bytes
- **WHEN** the bytes of the probe reach a stuck printer as part of its pending image
- **THEN** the call fails with `PrinterErrorType.printerNotReady` and no byte of the job follows them

### Requirement: Longer wait after an unconfirmed job
When the previous job on a printer ended with `confirmCompletion: false`, the probe SHALL wait up to `completionTimeout` of the current call instead of 2 seconds.

#### Scenario: Printer may still be busy
- **WHEN** a probe runs on a printer whose previous job ended with `confirmCompletion: false`
- **THEN** it waits up to `completionTimeout` for the answer

#### Scenario: Another printer is not affected
- **WHEN** a probe runs on a printer whose previous job was confirmed, after an unconfirmed job on a different printer
- **THEN** it waits at most 2 seconds

### Requirement: Opt-in probe in the three-step API
A print call of the three-step API SHALL run the probe only if the caller turns it on.

#### Scenario: Three-step call without the option
- **WHEN** a print call of the three-step API is made without turning the probe on
- **THEN** no probe is sent and the behaviour does not change

#### Scenario: Three-step call with the option
- **WHEN** a print call of the three-step API turns the probe on
- **THEN** the probe is answered before any byte of that call's job is sent

### Requirement: Printer not ready error type
The native code `PRINTER_NOT_READY` SHALL map to `PrinterErrorType.printerNotReady` in Dart. An unknown code SHALL still map to `PrinterErrorType.unknown`.

#### Scenario: Known code
- **WHEN** the native side reports `PRINTER_NOT_READY`
- **THEN** Dart throws a `PrinterException` of type `printerNotReady`

#### Scenario: Unknown code
- **WHEN** the native side reports a code Dart does not know
- **THEN** the type is `unknown`
