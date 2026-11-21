# Logictory

Logictory is a desktop logic-circuit sandbox built with Java Swing. Place and connect logic gates on a canvas, then see the circuit simulation update as inputs change. The freeplay board opens with a half-adder example.

## Features

- Drag-and-drop circuit editing with input, output, NOT, AND, OR, NAND, NOR, XOR, and XNOR gates
- Multi-select, undo/redo, and copy/cut/paste editing
- File, Edit, View, and Help menus with persisted preferences
- Save selected gates as named schematics, then browse or search saved and online schematics; copy a schematic to the clipboard
- Online tab seeded with example circuits (half/full adder, multiplexer, latch, NAND-only XOR) on first database setup
- Live circuit simulation and configurable wire styles
- Hold Space (or the middle mouse button) and drag to pan; wheel to zoom, plus fit-to-view
- Guest play without a database
- Optional MySQL-backed accounts and saved/community content

## Requirements

- JDK 27
- Apache Maven
- MySQL, only if using database-backed features

## Run

Start the application with Maven:

```sh
mvn compile exec:java -Dexec.mainClass=com.logictory.Main
```

The included `run.sh` runs the same command. To run the test suite:

```sh
mvn test
```

## Database configuration

At startup, Logictory attempts to connect to MySQL and create its schema. If the connection is unavailable, the application reports a warning and continues in guest mode. On the first run it also seeds a few example community schematics for the Online tab. Configure the connection with these environment variables:

| Variable | Default |
| --- | --- |
| `LOGICTORY_DB_URL` | `jdbc:mysql://localhost:3306/logictory?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true` |
| `LOGICTORY_DB_USER` | `root` |
| `LOGICTORY_DB_PASSWORD` | empty |

Set these variables in your shell before launching when your MySQL connection differs from the defaults. Avoid committing real credentials.
