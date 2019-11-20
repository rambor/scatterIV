package version4;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by robertrambo on 17/01/2016.
 */
public class LowerUpperBoundManager extends SwingWorker<Void, Integer> {

    private Collection collection;
    private int numberOfCPUs;
    private JProgressBar bar;
    private JLabel status;
    private int column;
    private double limit;
    private JTable list;

    public LowerUpperBoundManager(int numberOfCPUs, Collection collection, JProgressBar bar, JLabel label, int column, double limit){
        this.numberOfCPUs = numberOfCPUs;
        this.collection = collection;
        this.bar = bar;
        this.bar.setMaximum(collection.getTotalSelected());
        status = label;
        this.column = column;
        this.limit = limit;
    }

    public void boundNow(int column, double limit){

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfCPUs);

        List<Future<Bounder>> bounderFutures = new ArrayList<>();

        for (int i=0; i < collection.getTotalDatasets(); i++) {
            // create
            if (collection.getDataset(i).getInUse()){
                Future<Bounder> future = (Future<Bounder>) executor.submit(new Bounder(collection.getDataset(i), column, limit));
                bounderFutures.add(future);
            }
        }


        int completed = 0;
        for(Future<Bounder> fut : bounderFutures){
            try {
                // because Future.get() waits for task to get completed
                fut.get();
                //update progress bar
                completed++;
                setStatus("Finished truncating selected Datasets ");
                bar.setValue(completed);
                //publish(completed);
            } catch (InterruptedException | ExecutionException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }


        executor.shutdown();
//        while (!executor.isTerminated()) {
//
//        }
//
//        try {
//            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.SECONDS);
//            setStatus("Finished Setting q-limits");
//        } catch (InterruptedException e) {
//            System.out.println("InterruptedException " + e.getMessage());
//            setStatus("Failed settings limits, exceeded thread time");
//        }

//        for (int i=0; i < collection.getDatasetCount(); i++) {
//            //
//            if (collection.getDataset(i).getInUse()){
//                Dataset data = collection.getDataset(i);
//                if (column ==4){
//                    analysisModel.setValueAt(data.getStart(), data.getId(), column);
//                } else if (column ==5){
//                    analysisModel.setValueAt(data.getEnd(), data.getId(), column);
//                }
//            }
//        }

        for (int i=0; i < collection.getTotalDatasets(); i++) {
            if (collection.getDataset(i).getInUse()){
                Dataset data = collection.getDataset(i);
                data.setPlottedDataNotify(true);
            }
        }

        bar.setValue(0);
        bar.setStringPainted(false);
    }



    public void setStatus(String text){
        status.setText(text);
    }

    @Override
    protected Void doInBackground() throws Exception {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(numberOfCPUs);

        List<Future<Bounder>> bounderFutures = new ArrayList<>();
        for (int i=0; i < collection.getTotalDatasets(); i++) {
            // create
            if (collection.getDataset(i).getInUse()){
                Future<Bounder> future = (Future<Bounder>) executor.submit(new Bounder(collection.getDataset(i), column, limit));
                bounderFutures.add(future);
            }
        }


        int completed = 0;
        for(Future<Bounder> fut : bounderFutures){
            try {
                // because Future.get() waits for task to get completed
                fut.get();
                //update progress bar
                completed++;
                publish(completed);
            } catch (InterruptedException | ExecutionException e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
        }

        executor.shutdown();

        for (int i=0; i < collection.getTotalDatasets(); i++) {
            if (collection.getDataset(i).getInUse()){
                Dataset data = collection.getDataset(i);
                data.setPlottedDataNotify(true);
            }
        }

        bar.setValue(0);
        bar.setStringPainted(false);
        return null;
    }


    @Override
    protected void process(List<Integer> chunks) {
        int i = chunks.get(chunks.size()-1);
        bar.setValue(i);
        super.process(chunks);
    }

    @Override
    protected void done() {
        try {
            get();
            bar.setValue(0);
            bar.setStringPainted(false);
            status.setText("FINISHED TRUNCATING DATASETS");
            if (list != null){
                list.validate();
            }

        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }


    public void setJList(JTable list){
        this.list = list;
    }

}