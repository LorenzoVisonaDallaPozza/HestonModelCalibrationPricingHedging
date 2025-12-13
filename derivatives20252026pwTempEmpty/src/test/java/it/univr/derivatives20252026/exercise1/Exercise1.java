package it.univr.derivatives20252026.exercise1;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;

import it.univr.derivatives.marketdataprovider.MarketDataProvider;
import it.univr.derivatives.utils.TimeSeries;
import net.finmath.fouriermethod.calibration.BoundConstraint;
import net.finmath.fouriermethod.calibration.CalibratedModel;
import net.finmath.fouriermethod.calibration.ScalarParameterInformationImplementation;
import net.finmath.fouriermethod.calibration.CalibratedModel.OptimizationResult;
import net.finmath.fouriermethod.calibration.models.CalibratableHestonModel;
import net.finmath.fouriermethod.products.smile.EuropeanOptionSmileByCarrMadan;
import net.finmath.marketdata.model.curves.DiscountCurve;
import net.finmath.marketdata.model.volatilities.OptionSurfaceData;
import net.finmath.modelling.descriptor.HestonModelDescriptor;
import net.finmath.optimizer.OptimizerFactory;
import net.finmath.optimizer.OptimizerFactoryLevenbergMarquardt;
import net.finmath.time.businessdaycalendar.BusinessdayCalendar;
import net.finmath.time.businessdaycalendar. BusinessdayCalendarExcludingTARGETHolidays;

public class Exercise1 {

	public static void main(String[] args) throws Exception {
		
		// Load Market Data 
		TreeMap<LocalDate, OptionSurfaceData> marketData = MarketDataProvider.getVolatilityDataContainer();
		
		
		// All available dates
		Set<LocalDate> keys = marketData.keySet();
		
		// Create time series containers
		TimeSeries volatilityTimeSeries = new TimeSeries();
		TimeSeries thetaTimeSeries = new TimeSeries();
		TimeSeries kappaTimeSeries = new TimeSeries();
		TimeSeries xiTimeSeries = new TimeSeries();
		TimeSeries rhoTimeSeries = new TimeSeries();
		TimeSeries rmseTimeSeries = new TimeSeries();
		
		/**
		 * These are just initial guess to get the calibration started.
		 * The parameters specified here do not have an impact on the starting point of the calibration.
		 * The true initial condition is fixed by optimizer factory.
		 */
		final double volatility = 0.15;
		final double theta = 0.04;
		final double kappa = 1.0;
		final double xi = 0.5;
		final double rho = -0.5;
		
		
		/**
		 * Calibration configuration.
		 * Initial guess (Vol, Theta, Kappa, Xi, Rho) updated with Warm-start logic
		 */
		double[] currentParameters = new double[] { volatility, theta, kappa, xi, rho};
		final double[] parameterStep = new double[] { 0.0001, 0.0001, 0.0001, 0.0001, 0.0001};
		
		// Constraints set a bit larger so the optimizer doesn't touch the barriers to early
		final ScalarParameterInformationImplementation volatilityInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01, 1.0));
		final ScalarParameterInformationImplementation thetaInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01, 1.0));
		final ScalarParameterInformationImplementation kappaInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.1, 20.0));
		final ScalarParameterInformationImplementation xiInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(0.01, 5.0));
		final ScalarParameterInformationImplementation rhoInfo = new ScalarParameterInformationImplementation(true, new BoundConstraint(-0.99, 0.0));
		
		final OptimizerFactory optimizerFactory = new OptimizerFactoryLevenbergMarquardt(300, 2);
		
		// Standard convention of business days
		BusinessdayCalendar targetCalendar = new BusinessdayCalendarExcludingTARGETHolidays();
		
		
		// Calibration dates
        LocalDate start = LocalDate.of(2006, 1, 2);
        LocalDate limit = LocalDate.of(2006, 2, 2);
		
		// Initialize a counter for every wrong calibration
		int failures = 0; 
		
		// Daily calibration Loop
		for(LocalDate today : keys) {
			
			// This help us to start the calibration at a different date, i.e. not 2/01/2006
			if (today.isBefore(start)) continue;
			
			
			/**
			 * We skip a non business day.
			 * This control is useless in our case because the Dax data are already only in business day
			 */
			if(!targetCalendar.isBusinessday(today)) {
				System.out.println("Skipping buisness day" + today);
				continue;
			}
			
			// Breaking point of our loop
			if(today.isAfter(limit)) {
				break;
			}
			
			System.out.println("Processing: " + today);
			
			// Extract daily market data
			OptionSurfaceData data = marketData.get(today);
			
			// Extract curves and spot
			final DiscountCurve discountCurve = data.getDiscountCurve();
			final DiscountCurve equityForwardCurve = data.getEquityForwardCurve();
			final double initialValue = equityForwardCurve.getValue(0.0);
			
			// Create the descriptor
			HestonModelDescriptor hestonModelDescription = new HestonModelDescriptor(
					today, 
					initialValue, 
					discountCurve,    
					discountCurve, 
					currentParameters[0], currentParameters[1], currentParameters[2], currentParameters[3], currentParameters[4]);
			
			
			// Create the model
			final CalibratableHestonModel model = new CalibratableHestonModel( 
					hestonModelDescription,
					volatilityInfo, 
					thetaInfo, 
					kappaInfo, 
					xiInfo, 
					rhoInfo, 
					false);
			
			/**
			 *  Create Pricer (via Carr-Madan).
			 *  We fix a maturity used for optimization target
			 */
			final double maturityPricer = 63.0/252;
			final EuropeanOptionSmileByCarrMadan pricer = new EuropeanOptionSmileByCarrMadan(maturityPricer, new double[] { initialValue });
		
			// Create the calibration problem
			final CalibratedModel problem = new CalibratedModel(data, model, optimizerFactory, pricer, currentParameters, parameterStep);
			
			
			System.out.println("Calibration started...");
			
			// Execute the calibration
			final long startMillis	= System.currentTimeMillis();
			final OptimizationResult result = problem.getCalibration();
			final long endMillis = System.currentTimeMillis();
			final double calculationTime = ((endMillis-startMillis)/1000.0);

			System.out.println("Calibration completed in: " +calculationTime + " seconds");
			System.out.println("The solver required " + result.getIterations() + " iterations.");
			
			final double rmse = result.getRootMeanSquaredError();
			System.out.println("RMSE: " + rmse);
			
			// Validation and updates
			if(Double.isFinite(rmse) && rmse < 0.20) {
				
				// Success: extract calibrated parameters
				final HestonModelDescriptor hestonDescriptor = (HestonModelDescriptor) result.getModel().getModelDescriptor();
				
				// Output of the calibration for each parameter
				System.out.println("Volatility: " + hestonDescriptor.getVolatility());
				System.out.println("Theta: " + hestonDescriptor.getTheta());
				System.out.println("Kappa: " + hestonDescriptor.getKappa());
				System.out.println("Xi: " + hestonDescriptor.getXi());
				System.out.println("Rho: " + hestonDescriptor.getRho());
				
				// Update parameters for Warm Start, so next day uses today's result as initial guess
				currentParameters[0] = hestonDescriptor.getVolatility();
				currentParameters[1] = hestonDescriptor.getTheta();
				currentParameters[2] = hestonDescriptor.getKappa();
				currentParameters[3] = hestonDescriptor.getXi();
				currentParameters[4] = hestonDescriptor.getRho();
				
				// Update the time series
				volatilityTimeSeries.add(today, currentParameters[0]);
				thetaTimeSeries.add(today, currentParameters[1]);
				kappaTimeSeries.add(today, currentParameters[2]);
				xiTimeSeries.add(today, currentParameters[3]);
				rhoTimeSeries.add(today, currentParameters[4]);
				rmseTimeSeries.add(today, rmse);
				
				
				// Output for each maturity, market price, calibrated model price and errors
				final ArrayList<String> errorsOverview = result.getCalibrationOutput();

				for(final String myString : errorsOverview) {
					System.out.println(myString);
				}
				
				
				
			} else {
				
				// Failure: Reset parameters to default initial guess
				System.err.println("Calibration failed (High RMSE or NaN) for " + today);
				failures++;
				
				// Reset parameters to ensure next day doesn't start from a divergent point
				currentParameters = new double[] {0.15, 0.04, 1.0, 0.5, -0.5};
				rmseTimeSeries.add(today, 0.2); //set the RMSE to maximum tolerance to ensures the failure is visible in the graph
			}
			
			
			
		}
		
		System.out.println("Total Failures: " + failures);
		
		// Plotting time series for each parameter
		volatilityTimeSeries.plot("Volatility (Sigma)");
        thetaTimeSeries.plot("Theta (Long Run Vol)");
        kappaTimeSeries.plot("Kappa (Mean Reversion)");
        xiTimeSeries.plot("Xi (Vol of Vol)");
        rhoTimeSeries.plot("Rho (Correlation)");
        rmseTimeSeries.plot("Calibration RMSE");
	}
}