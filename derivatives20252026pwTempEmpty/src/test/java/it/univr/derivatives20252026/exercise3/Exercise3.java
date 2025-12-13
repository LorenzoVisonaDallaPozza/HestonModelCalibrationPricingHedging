package it.univr.derivatives20252026.exercise3;

import java.time.LocalDate;
import java.util.Set;
import java.util.TreeMap;

import it.univr.derivatives.marketdataprovider.MarketDataProvider;
import net.finmath.exception.CalculationException;
import net.finmath.fouriermethod.calibration.BoundConstraint;
import net.finmath.fouriermethod.calibration.CalibratedModel;
import net.finmath.fouriermethod.calibration.ScalarParameterInformationImplementation;
import net.finmath.fouriermethod.calibration.CalibratedModel.OptimizationResult;
import net.finmath.fouriermethod.calibration.models.CalibratableHestonModel;
import net.finmath.fouriermethod.products.EuropeanOption;
import net.finmath.fouriermethod.products.smile.EuropeanOptionSmileByCarrMadan;
import net.finmath.functions.HestonModel;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.volatilities.OptionSurfaceData;
import net.finmath.modelling.descriptor.HestonModelDescriptor;
import net.finmath.optimizer.OptimizerFactory;
import net.finmath.optimizer.OptimizerFactoryLevenbergMarquardt;
import net.finmath.optimizer.SolverException;
import net.finmath.time.daycount.DayCountConvention;
import net.finmath.time.daycount.DayCountConvention_ACT_365;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar;
import net.finmath.time.businessdaycalendar. BusinessdayCalendarExcludingTARGETHolidays;

public class Exercise3 {
	
	public static void main(String[] args) throws SolverException, CalculationException {
		
		// Load Market Data
		TreeMap<LocalDate, OptionSurfaceData> marketData = MarketDataProvider.getVolatilityDataContainer();
		
		// All available dates
		Set<LocalDate> keys = marketData.keySet();
		BusinessdayCalendar targetCalendar = new BusinessdayCalendarExcludingTARGETHolidays();
				
		
		/**
		 *  Step 1: Calibration.
		 */
	
		// Reference Day
		LocalDate today = LocalDate.of(2006, 1, 2);
		/*
		 * The while loop verifies whether the selected date is a valid business day. 
		 * If the reference date falls on a non-business day, 
		 * the loop advances to the next available trading day.
		 */
		while(true) {
			if(!targetCalendar.isBusinessday(today)) {
				today = today.plusDays(1);
				}else {
					break;
					}
			}
		
		double volatility = 0.15;
		double theta = 0.04;
		double kappa = 1.0;
		double xi = 0.5;
		double rho = -0.5;
		
		OptionSurfaceData data = marketData.get(today);
		DiscountCurve discountCurve = data.getDiscountCurve();
		DiscountCurve equityFowardCurve = data.getEquityForwardCurve();

		final double initialValue = equityFowardCurve.getValue(0.0);
		
		HestonModelDescriptor hestonModelDescription = new HestonModelDescriptor(today, initialValue, discountCurve, discountCurve, volatility, theta, kappa, xi, rho);
		
		final ScalarParameterInformationImplementation volatilityInformation = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01,1.0));
		final ScalarParameterInformationImplementation thetaInformation = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01,1.0));
		final ScalarParameterInformationImplementation kappaInformation = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01,20.0));
		final ScalarParameterInformationImplementation xiInformation = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01,5));
		final ScalarParameterInformationImplementation rhoInformation = new ScalarParameterInformationImplementation(true, new BoundConstraint(-0.99,0.99));
		
		final CalibratableHestonModel model = new CalibratableHestonModel( 
				hestonModelDescription,
				volatilityInformation,
				thetaInformation,
				kappaInformation,
				xiInformation,
				rhoInformation,
				false);
		
		final OptimizerFactory optimizerFactory = new OptimizerFactoryLevenbergMarquardt(300, 2);
		final double maturityForPricer = 30.0/252;
		final EuropeanOptionSmileByCarrMadan pricer = new EuropeanOptionSmileByCarrMadan(maturityForPricer, new double [] {initialValue});
		final double[] currentParameters = new double[] { volatility, theta, kappa, xi, rho};
		final double[] parameterStep = new double[] { 0.001,0.001,0.001,0.001,0.001};
		
		final CalibratedModel problem = new CalibratedModel(data, model, optimizerFactory, pricer, currentParameters, parameterStep);
		final OptimizationResult result = problem.getCalibration();
		final HestonModelDescriptor calibratedHestonDescriptor = (HestonModelDescriptor) result.getModel().getModelDescriptor();
		
		volatility = calibratedHestonDescriptor.getVolatility();
		theta = calibratedHestonDescriptor.getTheta();
		kappa = calibratedHestonDescriptor.getKappa();
		xi = calibratedHestonDescriptor.getXi();
		rho = calibratedHestonDescriptor.getRho();

		
		/*
		 * Step 2: We sell today a call option on DAX with maturity 2 months and strike equals to 95% of spot price
		 */
		
		// Define the duration of the hedging strategy
		int months = 2;
		
		// Define the maturity of the option
		double optionMaturity = (double)months/12.0;
		
		// Define the strike of the option
		double optionStrike = initialValue*0.95;
		
		// Retrieve the risk-free rate of the curve
		double riskFreeRate = ((net.finmath.marketdata.model.curves.DiscountCurveInterpolation) discountCurve).getZeroRate(optionMaturity);
		
		// Create the Heston Model
		net.finmath.fouriermethod.models.HestonModel modelHS = 
				new net.finmath.fouriermethod.models.HestonModel (initialValue, riskFreeRate, volatility, riskFreeRate, theta, kappa, xi , rho);
		
		// Create the Call Option
		EuropeanOption callOptionOnDax = new EuropeanOption(optionMaturity, optionStrike);
		
		// Compute the pricing of the call using Heston Model
		double sellingPrice = callOptionOnDax.getValue(0.0, modelHS);
		System.out.println("We sell an option with maturity " + months  + " months, strike  " + 
		                   optionStrike + ", underlying spot price " + initialValue + " and price " + sellingPrice);
		
		System.out.println();
		
		// This is when we stop the hedging portfolio
		LocalDate maturity = today.plusMonths(months);
		/*
		 * The while loop verifies whether the selected maturity is a valid business day. 
		 * If the maturity falls on a non-business day, 
		 * the loop advances to the next available trading day
		 */
		while(true) {
			if(!targetCalendar.isBusinessday(maturity)) {
				maturity = maturity.plusDays(1);
				}else {
					break;
					}
			}
		
		// Define the day count convention for update the numeraire
		DayCountConvention daycountConvention = new DayCountConvention_ACT_365();
		
		
		/*
		 * Step 3: Delta Hedge
		 */
		
		// Define the value of numeraire asset at time 0
		double bankAccountAtTimeIndex = 1.0;
		
		// Initialize the values of underlying asset
		double underlyingAtTimeIndex = 0.0;
		
		// Calculate the initial quantity of the numeraire units
		double amountOfBankAccount = sellingPrice/bankAccountAtTimeIndex;
		
		// Initialize the quantity of the underlying asset at time zero
		double amountOfUnderlyingAsset = 0.0;
		
		// Initialize the delta of Heston Model
		double delta = 0.0;
		
		// Counts the number of hedging (business) days 
		int hedgingDays = 0;
		
		LocalDate previousDate = today;
		
		/*
		 * This loop executes the dynamic hedging strategy from the initial time 0 
		 * up to time T-1 (the trading day immediately preceding maturity).
		 */
		for(LocalDate currentDay : keys) {
			if(currentDay.isBefore(today)) continue;
			if(currentDay.plusDays(1).isAfter(maturity)) break;
			
			// Calculate the year fraction corresponding to the time step Δt, i.e. days between two consecutive business days
			double dayCountFraction = daycountConvention.getDaycountFraction(previousDate, currentDay);
			
			// Get the current value of the underlying from the data
			OptionSurfaceData currentDayData = marketData.get(currentDay);
			underlyingAtTimeIndex = currentDayData.getEquityForwardCurve().getValue(0.0);
			
			// Update the numeraire to include the latest data
			bankAccountAtTimeIndex *= Math.exp(riskFreeRate*dayCountFraction);
			
			// Time To maturity for the delta calculation
			double timeToMaturity = daycountConvention.getDaycountFraction(currentDay, maturity);
			
			// Compute the delta 
			delta = HestonModel.hestonOptionDelta(100.0, riskFreeRate, 0.0, volatility, theta, kappa, xi, rho, timeToMaturity, optionStrike/underlyingAtTimeIndex*100);
			
			// Set new number of stocks and changes in quantity
			double newNumberOfStock = delta;
			double stocksToBuy = newNumberOfStock - amountOfUnderlyingAsset;
			
			// Compute the variation of the numeraire required to finance the trade
			double numeraireToSell = stocksToBuy*underlyingAtTimeIndex/bankAccountAtTimeIndex;
			
			// Set updated quantity of the numeraire asset
			double newNumberOfNumeraireAsset = amountOfBankAccount - numeraireToSell;
			
			// Strategies to implement
			System.out.println();
			System.out.println("Strategy to implement for: " + currentDay);
			System.out.println("Holds in asset: " + newNumberOfStock);
			System.out.println("Stocks to buy: " + stocksToBuy);
			System.out.println("Holds in numeraire: " + newNumberOfNumeraireAsset);
			System.out.println("Numeraire to sell: " + numeraireToSell);
			System.out.println();
			
			// Update the portfolio holdings for the next step
			amountOfBankAccount = newNumberOfNumeraireAsset;
			amountOfUnderlyingAsset = newNumberOfStock;
		
			// Update the reference date for the next iteration
			previousDate = currentDay;
			
			// Update the day counter 
			hedgingDays++;
			
		}
		
		System.out.println("Hedging days: " + hedgingDays);
		
		// Retrieve the market data available at maturity
		OptionSurfaceData finalDayData = marketData.get(maturity);
		
		// Retrieve the underlying spot price at maturity (S_T)
		double underlyingAtMaturity  = finalDayData.getEquityForwardCurve().getValue(0.0);
		
		// Compute the final year fraction corresponding to the last time step (T - (T-1))
		double finalTimeStep = daycountConvention.getDaycountFraction(previousDate, maturity);
		
		// Compute the numeraire at maturity
		bankAccountAtTimeIndex *= Math.exp(riskFreeRate * finalTimeStep);
		
		// Compute the final portfolio value
		double portfolioValue = (amountOfBankAccount*bankAccountAtTimeIndex) + (amountOfUnderlyingAsset*underlyingAtMaturity);
		
		// Compute the payoff of the option at maturity
		double payoffAtMaturity = Math.max(underlyingAtMaturity-optionStrike, 0.0);
		
		System.out.println();
		
		// Validation of hedging strategy
		System.out.println("Underlying at maturity " + underlyingAtMaturity);
		System.out.println("Strike of the option " + optionStrike);
		System.out.println("The final value of the portfolio is " + portfolioValue);
		System.out.println("The payoff of the option is " + payoffAtMaturity);
		System.out.println("The hedging error is  " + (portfolioValue - payoffAtMaturity));
		
	}

}