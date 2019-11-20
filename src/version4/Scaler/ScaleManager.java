package version4.Scaler;

import org.jfree.data.xy.XYDataItem;
import org.jfree.data.xy.XYSeries;
import version4.Collection;
import version4.Dataset;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by xos81802 on 26/07/2017.
 */
public class ScaleManager extends SwingWorker<Void, Void> {

    private JProgressBar progressBar;
    private int cpus;
    private JLabel label;
    private Collection collectionInUse;
    private double lower, upper;

    public ScaleManager(int numberOfCPUs, Collection collection, JProgressBar bar, JLabel label){
        collectionInUse = collection;
        this.cpus = numberOfCPUs;
        this.label = label;
        this.progressBar = bar;
    }

    /**
     * set the q-window range used for scaling the data
     * should exclude particularly noisy regions.
     *
     * @param lowerq
     * @param upperq
     */
    public void setUpperLowerQLimits(double lowerq, double upperq){
        this.lower = lowerq;
        this.upper = upperq;
    }

    @Override
    protected Void doInBackground() throws Exception {

        // need to determine reference state, set scale to 1 and then
        // reference is large Intensity between 0.017 and 0.1
        ArrayList<Scaler> scalerModels = new ArrayList<>();
        int totalDatasetsInCollection = collectionInUse.getTotalDatasets();
        int refIndex = 0;
        double maxInt = 0.0d;
        int totalToCalculate=0;

        progressBar.setStringPainted(true);
        progressBar.setValue(0);
        progressBar.setMaximum(totalDatasetsInCollection);
        label.setForeground(Color.GREEN);
        label.setText("Determining reference frame");

        for(int i=0; i<totalDatasetsInCollection; i++){  // find frame with largest Intensity within range

            Dataset tempDatset = collectionInUse.getDataset(i);
            XYDataItem xyItem;

            if (tempDatset.getInUse()){
                totalToCalculate++;
                int totalInData = tempDatset.getAllData().getItemCount();  // in case lower is larger than user specified by spinners
                double templowest = tempDatset.getOriginalPositiveOnlyDataItem(tempDatset.getStart()).getXValue();
                if (templowest < lower){
                    lower = templowest;
                }

                IntensityLoop:
                for(int j=0; j< totalInData; j++){
                    xyItem = tempDatset.getAllData().getDataItem(j);
                    // need to check if
                    if (xyItem.getXValue() > 0.017 && xyItem.getXValue() < 0.15 && xyItem.getYValue() > maxInt){
                        maxInt = xyItem.getYValue();
                        refIndex = i;
                        break IntensityLoop;
                    } else if (xyItem.getXValue() > 0.15) {
                        break IntensityLoop;
                    }
                }
            }
            progressBar.setValue(i);
        }


        progressBar.setValue(0);
        progressBar.setMaximum(totalDatasetsInCollection);

        Dataset referenceDataset = collectionInUse.getDataset(refIndex);
        referenceDataset.setScaleFactor(1.0);
//        referenceDataset.scalePlottedLog10IntensityData();

        XYSeries referenceXY = referenceDataset.getOriginalPositiveOnlyData();

        int startRefIndex = referenceDataset.getStart()-1;
        int endRefIndex = referenceDataset.getEnd()-1;

        // calculate scale factors for each dataset in use
        ScheduledExecutorService scalerExecutor = Executors.newScheduledThreadPool(cpus);

        List<Future<Scaler>> scalerFutures = new ArrayList<>();
        label.setText("Building Threads ");

        for(int i=0; i<totalDatasetsInCollection; i++){
            if (collectionInUse.getDataset(i).getInUse() && i != refIndex){

                Dataset target = collectionInUse.getDataset(i);
                Future<Scaler> future = scalerExecutor.submit(new CallableScaler(
                        referenceXY,
                        target,
                        startRefIndex,
                        endRefIndex,
                        lower,
                        upper
                ));

                scalerFutures.add(future);
            }
            progressBar.setValue(i);
        }

        progressBar.setValue(0);
        progressBar.setMaximum(totalToCalculate);

        int completed = 0;
        for(Future<Scaler> fut : scalerFutures){
            try {
                // because Future.get() waits for task to get completed
                scalerModels.add(fut.get());
                //update progress bar
                completed++;
                progressBar.setValue(completed);
                label.setText("Scaled " + completed);
                //publish(completed);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        scalerExecutor.shutdown();
        label.setText("Scaled to " + (refIndex + 1) );
        return null;
    }


}