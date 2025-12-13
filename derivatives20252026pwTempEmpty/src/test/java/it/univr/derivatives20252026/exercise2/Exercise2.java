package it.univr.derivatives20252026.exercise2;

import java.time.LocalDate;
import java.util.TreeMap;
import java.util.function.DoubleUnaryOperator;

import it.univr.derivatives.marketdataprovider.MarketDataProvider;
import net.finmath.exception.CalculationException;
import net.finmath.fouriermethod.calibration.BoundConstraint;
import net.finmath.fouriermethod.calibration.CalibratedModel;
import net.finmath.fouriermethod.calibration.ScalarParameterInformationImplementation;
import net.finmath.fouriermethod.calibration.CalibratedModel.OptimizationResult;
import net.finmath.fouriermethod.calibration.models.CalibratableHestonModel;
import net.finmath.fouriermethod.models.HestonModel;
import net.finmath.fouriermethod.products.smile.EuropeanOptionSmileByCarrMadan;
import net.finmath.integration.TrapezoidalRealIntegrator;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.volatilities.OptionSurfaceData;
import net.finmath.modelling.descriptor.HestonModelDescriptor;
import net.finmath.optimizer.OptimizerFactory;
import net.finmath.optimizer.OptimizerFactoryLevenbergMarquardt;
import net.finmath.optimizer.SolverException;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar;
import net.finmath.time.businessdaycalendar.BusinessdayCalendarExcludingTARGETHolidays;

public class Exercise2 {

	public static void main(String[] args) throws SolverException, CalculationException {
		
		// Calibrate one day to get the parameters
		TreeMap<LocalDate, OptionSurfaceData> marketData = MarketDataProvider.getVolatilityDataContainer();
		BusinessdayCalendar targetCalendar = new BusinessdayCalendarExcludingTARGETHolidays();
		LocalDate today = LocalDate.of(2006, 1, 2);
		OptionSurfaceData data = marketData.get(today);
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
		
		double[] currentParameters = new double[] { volatility, theta, kappa, xi, rho};
		final double[] parameterStep = new double[] { 0.0001, 0.0001, 0.0001, 0.0001, 0.0001};
				
		final DiscountCurve discountCurve = data.getDiscountCurve();
		final DiscountCurve equityForwardCurve = data.getEquityForwardCurve();
		double initialValue = equityForwardCurve.getValue(0.0);
		
		
		HestonModelDescriptor hestonModelDescription = new HestonModelDescriptor(today, initialValue, discountCurve, discountCurve, volatility, 
				theta, kappa, xi, rho);
		
		final ScalarParameterInformationImplementation volatilityInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01, 1.0));
		final ScalarParameterInformationImplementation thetaInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01, 1.0));
		final ScalarParameterInformationImplementation kappaInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.1, 20.0));
		final ScalarParameterInformationImplementation xiInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01, 5.0));
		final ScalarParameterInformationImplementation rhoInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(-0.99, 0.0));
		
		final CalibratableHestonModel model = new CalibratableHestonModel( 
				hestonModelDescription,
				volatilityInfo, 
				thetaInfo, 
				kappaInfo, 
				xiInfo, 
				rhoInfo, 
				false);
		
		final OptimizerFactory optimizerFactory = new OptimizerFactoryLevenbergMarquardt(300, 2);
		
		final double maturityPricer = 63.0/252;
		
		final EuropeanOptionSmileByCarrMadan pricer = new EuropeanOptionSmileByCarrMadan(maturityPricer, new double[] { initialValue });
	
		final CalibratedModel problem = new CalibratedModel(data, model, optimizerFactory, pricer, currentParameters, parameterStep);
		
		final OptimizationResult result = problem.getCalibration();
		final HestonModelDescriptor hestonDescriptor = (HestonModelDescriptor) result.getModel().getModelDescriptor();
		
		volatility = hestonDescriptor.getVolatility();
		theta = hestonDescriptor.getTheta();
		kappa = hestonDescriptor.getKappa();
		xi = hestonDescriptor.getXi();
		rho = hestonDescriptor.getRho();
		
		
		// Set the contract maturity
		double maturity = 30.0/252;
		
		
		/**
		 *  First approach: analytic formula.
		 *  Expectation of the integrated variance process under Heston dynamics
		 */
		
		// Compute the price via formula (5)
		double fairVarSwapRate = ((1-Math.exp(-kappa*maturity))/(kappa*maturity))*(volatility*volatility-theta)+theta;
		
		System.out.println(" --- Formula (5): Analytic approach ---");
		
		System.out.println("The fair Variance Swap Rate (5) is: " + fairVarSwapRate);
		
		
		/**
		 * Second approach : static replication. Carr-Madan replication
		 */
		
		// Scaling Spot for numerical stability in integration
		double daxSpotPrice = equityForwardCurve.getValue(0.0)/100;
		double riskFreeRate = 0.0;
		double discountRate = riskFreeRate;
		double daxForwardPrice = daxSpotPrice*Math.exp(riskFreeRate*maturity);
		
		// Create the characteristic function for pricing with calibrated parameters
		HestonModel modelHS = new HestonModel(daxSpotPrice, riskFreeRate, volatility, discountRate, theta, kappa, xi, rho);
		
		// Create the resolver for the Call integral parts
		TrapezoidalRealIntegrator integratorCalls2 = new TrapezoidalRealIntegrator(daxForwardPrice, 5*daxForwardPrice, 3000);
		
		// Create the resolver for the Put integral parts
		TrapezoidalRealIntegrator integratorPuts2 = new TrapezoidalRealIntegrator(0.01*daxForwardPrice, daxForwardPrice, 3000);
		
		// Define integrand for Calls
		DoubleUnaryOperator integrandCalls2 = x ->
		{
			try {
				return new net.finmath.fouriermethod.products.EuropeanOption(maturity, x).getValue(modelHS)/(x*x);
			} catch (CalculationException e) {
				e.printStackTrace();
			}
			return 0.0;
		};
		
		// Define integrand for Puts
		DoubleUnaryOperator integrandPuts2 = x -> 
		{
		    try {
		    	// Compute the calls price
		        double priceCall = new net.finmath.fouriermethod.products.EuropeanOption(maturity, x)
		                .getValue(modelHS);
		        // Use put-call parity to compute puts price: Put = Call - spotPrice + discountedStrike
		        double pricePut = priceCall - daxSpotPrice + (x*Math.exp(-riskFreeRate*maturity));

		        return pricePut / (x * x);
		        } catch (CalculationException e) {
		        	e.printStackTrace();
				}
				return 0.0;
			};
		
		// Perform integration
		double integratedPut = integratorPuts2.integrate(integrandPuts2);
		double integratedCall = integratorCalls2.integrate(integrandCalls2);
		
		System.out.println(" --- Formula (6): Static approach ---");
		
		// Compute the price via formula (6)
		double finalPrice = 2.0*((riskFreeRate*maturity)-Math.log(daxForwardPrice/daxSpotPrice)
				+Math.exp(riskFreeRate*maturity)*integratedPut+Math.exp(riskFreeRate*maturity)*integratedCall);
		double fairPrice2 = finalPrice/maturity;
		
		System.out.println("The Fair VarSwap Rate (6) is: " + fairPrice2);
		
		// Validations
		System.out.println("Difference vs Analytical: " + (Math.abs(fairVarSwapRate - fairPrice2)));
		System.out.println("Relative difference with analytic: " + (Math.abs(fairVarSwapRate - fairPrice2)/fairVarSwapRate)*100 + "%");
	
		
	}

}
