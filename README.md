# Heston Model: Calibration, Pricing & Hedging

> **Project developed for the "Derivatives" course at Università di Verona.**
>
> **⚠️ Status:** *Code Complete & Submitted - Pending Academic Review.*

## Project Overview
This Java project is designed to perform a comprehensive analysis of the **Heston Stochastic Volatility Model**, moving beyond theoretical examples to apply the model to real market scenarios.

The software adheres to Object-Oriented Programming (OOP) principles and leverages the **Finmath Library** for stochastic modeling. The project is structured into three main exercises, covering model parameter calibration, derivative pricing, and hedging strategies.

## Key Features
* **Model Calibration (Exercise 1)**: Implementation of a calibration algorithm to fit Heston model parameters ($\kappa$, $\theta$, $\xi$, $\rho$, $v_0$) to market volatility surfaces over a specific time horizon.
    * **Data Source**: Calibrated on real market data (e.g., DAX index options) imported via CSV.
* **Derivative Pricing (Exercise 2)**: Valuation of **Variance Swaps** using two distinct methodologies:
    * **Analytic Formula**: Closed-form pricing based on the Heston model parameters.
    * **Static Replication**: Pricing via a replicating portfolio of vanilla options (Call and Put) across strikes (Carr-Madan approach).
* **Delta Hedging Simulation (Exercise 3)**: Simulation of a daily **Delta Hedging** strategy over an investment horizon.
    * **Process**: Daily portfolio rebalancing to neutralize delta exposure.
    * **Analysis**: Evaluation of the hedging error between the strategy's P&L and the target exposure.

## Project Structure
The project follows the standard Maven directory structure and separates the student's implementation from the provided infrastructure:

* `it.univr.derivatives20252026.exercise1`: Contains the **Calibration logic**.
* `it.univr.derivatives20252026.exercise2`: Contains the **Pricing logic** (Analytic & Static Replication).
* `it.univr.derivatives20252026.exercise3`: Contains the **Hedging logic**.
* `it.univr.derivatives.marketdataprovider`: Utilities for data ingestion and processing.
* `net.finmath.functions`: Core mathematical functions for the Heston model.

## Tech Stack
* **Java 17**
* **Maven**: Dependency management.
* **Finmath Lib**: Library for financial mathematics and stochastic processes.
* **Apache Commons Math**: Used for optimization algorithms in calibration.

## Authors & Acknowledgments

**Development Team:**
* Lorenzo Visonà Dalla Pozza
* Alberto Oliva Medin
* Davide Barberis

**Credits:**
* This project is based on a template provided by **Prof. Alessandro Gnoatto**, specifically regarding the **infrastructure and data providers** (`MarketDataProvider`, `TimeSeries`).
* The implementation of **Calibration**, **Variance Swap Pricing** (Analytic/Static), and **Delta Hedging** was developed by the team as part of the project work.
