# Contributing to Mocapi Enterprise Demo

Thanks for your interest in this project! It's an enterprise-grade reference example of an MCP server built with Spring Boot 4 and [Mocapi](https://github.com/callibrity/mocapi). We welcome pull requests, issues, and feedback.

## How to Contribute

### Reporting Bugs

If you find a bug, please [open an issue](https://github.com/callibrity/mocapi-enterprise-demo/issues/new) and include:

- A clear description of the problem
- Steps to reproduce the issue
- Expected vs actual behavior
- Version of the connector and relevant environment details (JDK, OS, Docker version)

### Requesting Features or Tools

This project is a teaching example, so the bar for new tools is "does it illustrate something a real catalog connector would expose?" Open an issue describing:

- The catalog question the new tool would answer
- Why an existing tool doesn't already cover it
- Any relevant use cases

### Submitting a Pull Request

1. **Fork** the repository and create a new branch from `main`.
2. Make your changes, writing tests if applicable.
3. Run the build and tests to make sure everything passes:

   ```bash
   mvn verify
   ```

   This runs the test suite and enforces both license headers (`license:check`) and code formatting (`spotless:check`). To auto-apply both:

   ```bash
   mvn license:format spotless:apply
   ```

4. Open a pull request and describe your changes.

For larger changes, consider opening an issue first to discuss the approach.

## Project Structure

- `src/main/java/com/callibrity/mocapi/demo/catalog/` — domain model, repositories, service layer, MCP tool adapter, seed data
- `src/main/java/com/callibrity/mocapi/demo/feedback/` — self-improvement tools (`submit-feedback`, `suggest-tool`) for evolving the server based on LLM-reported friction
- `src/test/java/` — unit tests mirroring the main package layout

## Code Style and Conventions

- Java 25 features are fair game — prefer clarity and simplicity
- Format is enforced by Spotless using Google Java Format; run `mvn spotless:apply` before committing
- License headers are enforced by mycila's license-maven-plugin; run `mvn license:format` to add them automatically
- Public methods and classes should be Javadoc'd
- Keep method visibility as narrow as possible

## Community Standards

We strive to foster a welcoming and respectful community. By participating, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

## License

By contributing to this project, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
