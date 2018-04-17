# Simple Market Maker in Java

<a href="https://travis-ci.org/quedexnet/java-market-maker/"><img src="https://travis-ci.org/quedexnet/java-market-market.svg?branch=master" align=right></a>


An implementation of a dummy market making bot which intends to be a starting point showing a possible way of 
integration with Quedex Java API. Before starting to work with this code, please read the 
[documentation][java-api-docs] of our Java API.

The implementation of the bot MUST NOT be considered complete and ready for production use. The pricing algorithms, risk
management, handling of WebSocket disconnects (lack thereof), etc. are just to illustrate a simple use case. 

## Running

To run the bot you need to have Java 8 installed (Oracle Java is recommended).

### Standalone JAR

The following steps are for Linux (tested on Ubuntu 16.04):

1. Execute `./gradlew shadowJar` from the main project directory (`java-market-maker`).
2. Jar will be created in `java-market-maker/build/libs/` named `java-market-maker-<version>-all.jar`.
3. Copy the jar to a convenient location, place your `quedex-config.properties` and `market-maker.properties`
   (examples may be found in `java-market-maker/src/main/resources`) next to it.
4. Run the jar with `java -jar java-market-maker-<version>-all.jar quedex-config.properties market-maker.properties`. To
   exit hit CTRL + C.

### From an IDE

0. Clone the repository.
1. Import the gradle project to your favourite IDE (tested with IntelliJ).
2. Fetch the dependencies (should happen automatically).
3. Rename the file `quedex-config.properties.example` in `java-market-maker/src/main/resources` to 
`quedex-config.properties` and fill in your details.
4. Rename the file `market-maker.properties.example` in `java-market-maker/src/main/resources` to
`market-maker.properties` and change the configuration according to your liking.
5. Run the `Main` class.

## Features

The market making bot:
* places orders with configurable quantities on configurable number of levels,
* has configurable spread,
* follows a predefined Fair Price for futures (currently last price or mid - change the implementation in the 
`MarketMaker` class between `LastFairPriceProvider` and `MidFairPriceProvider`),
* places option orders priced according to Black 76' model,
* has configurable risk management - stops quoting one side of the order book when delta or vega limit exceeded,
* enables risk monitoring based on greeks (delta, vega, gamma, theta), per position and in total,
* cancels all orders when going down or on error.

## Disclaimer

This document and the code presented in this repository does not constitute any investment advice. By running it, you 
are not guaranteed to earn any bitcoins (rather the opposite).

## Contributing Guide

Default channel for submitting **questions regarding the bot** is [opening new issues][new-issue].
In cases when information disclosure is&nbsp;not possible, you can contact us at support@quedex.net.

Pull requests containing bugfixes are very welcome!

## License

Copyright &copy; 2017 Quedex Ltd. The bot is released under [Apache License Version 2.0](LICENSE).

[java-api-docs]: https://github.com/quedexnet/java-api
[new-issue]: https://github.com/quedexnet/python-api/issues/new
