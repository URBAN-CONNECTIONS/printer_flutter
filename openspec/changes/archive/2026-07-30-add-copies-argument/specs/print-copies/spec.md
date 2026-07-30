## ADDED Requirements

### Requirement: Print Copies Argument
The system SHALL support an optional `copies` argument for print operations that dictates the number of copies printed. 

#### Scenario: User requests a specific number of copies using unifiedRoll
- **GIVEN** a valid connection to a TSPL printer
- **AND** the `unifiedRoll` printing strategy is selected
- **WHEN** the user calls a print method with `copies` set to a value greater than 1
- **THEN** the system issues a single `PRINT <copies>,1` command for the stitched document

#### Scenario: User requests a specific number of copies using pageByPage
- **GIVEN** a valid connection to a TSPL printer
- **AND** the `pageByPage` printing strategy is selected
- **WHEN** the user calls a print method with `copies` set to a value greater than 1
- **THEN** the system SHALL collate the output by transmitting the entire sequence of pages (e.g. Page 1, Page 2) to the printer repeatedly, `copies` times.

#### Scenario: User does not specify the number of copies
- **GIVEN** a valid connection to a TSPL printer
- **WHEN** the user calls a print method without specifying `copies`
- **THEN** the system issues a `PRINT 1,1` command and processes the document sequence once by default
