package com.monte.indoorpositioning;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.view.View;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

/**
 * Created by monte on 06/04/2017.
 *
 * Graph manager class shows the current acceleration and walked distance in the graph form.
 * This allows the testers to improve the algorithm and
 */
public class GraphManager {
    // One series is one single line some parameters
    private LineGraphSeries<DataPoint> series1; // first series shows the acceleration in Z axis (gravity axis)
    private LineGraphSeries<DataPoint> series2; // second shows the acceleration in walking direction (X axis)
    private LineGraphSeries<DataPoint> series3; // third shows the total walked distance

    // to handle the graoh drawing a runnable and a handler are required
    private Runnable rawValuesTimer;
    private final Handler mHandler = new Handler(); //for handling thread

    private GraphView graphView;                // graph view is the view of the graph which is being shown

    private float data[] = new float[3];        // passed data to be drawn on the map
    int graph2LastXValue = 0;                   // x coordinate value of the graph
    /**
     *  Constructor of the class required passing the graph from the xml
     */
    public GraphManager(GraphView graphView) {
        this.graphView = graphView;
    }

    /**
     * function to be called when drawing is no longer needed
     */
    public void stopDrawing(){
        mHandler.removeCallbacks(rawValuesTimer);
    }

    /**
     * function called when drawing of the function is to begin
     */
    public void startDrawing(){
        // implemented runnable method
        rawValuesTimer = new Runnable() {
            @Override
            public void run() {
                // update x value
                graph2LastXValue += 1d;
                // add data to the series to be drawn, which is being drawn automatically afterwards
                series1.appendData(new DataPoint(graph2LastXValue, data[0]), true, 150);
                series2.appendData(new DataPoint(graph2LastXValue, data[1]), true, 150);
                series3.appendData(new DataPoint(graph2LastXValue, data[2]), true, 150);
                // handler makes updates to drawing every 20 milliseconds
                mHandler.postDelayed(this, 20);
            }
        };
        mHandler.postDelayed(rawValuesTimer, 1000);
    }

    /**
     * Set the visibility for the graph from external
     * @param status
     */
    public void setVisibility(int status){
        graphView.setVisibility(status);
    }

    /**
     * function used to updates values to be drawn from external activity
     * @param data
     */
    public void updateValues (float[] data){
        this.data = data;
    }

    /**
     * Setupping the graphs is perform here. The graph manages series, series colors, title of the graph etc.
     */
    public void setupGraphs(){
        // Firstly create new line series
        series1 = new LineGraphSeries<>();
        series2 = new LineGraphSeries<>();
        series3 = new LineGraphSeries<>();

        // set title for series (legend)
        series1.setTitle("Z Acc");
        series2.setTitle("Distance");
        series3.setTitle("X Acc");
        // show the legend and set some settings for it
        graphView.getLegendRenderer().setVisible(true);             // make legend visible
        graphView.getLegendRenderer().setFixedPosition(0, -50);     // set the position of the legend
        graphView.getLegendRenderer().setTextSize(35);              // set text size
        graphView.getLegendRenderer().setSpacing(6);                // change spacing and padding
        graphView.getLegendRenderer().setPadding(6);
        graphView.getLegendRenderer().setBackgroundColor(R.color.transparent);// make background transparent

        // set the colour for series (different color lines)
        series1.setColor(Color.BLACK);
        series2.setColor(Color.RED);
        // series3 has the default color of blue

        // add series to the graph
        graphView.addSeries(series1);
        graphView.addSeries(series2);
        graphView.addSeries(series3);

        // set the title of the graph
        graphView.setTitle("Walked Distance");

        // set limits on the Y axis manually. Set it to be from -10 to 10
        graphView.getViewport().setYAxisBoundsManual(true);
        graphView.getViewport().setMinY(-10);
        graphView.getViewport().setMaxY(10);

        // similarly show only limited x axis from 0 to 150.
        graphView.getViewport().setXAxisBoundsManual(true);
        graphView.getViewport().setMinX(0);
        graphView.getViewport().setMaxX(150);
    }
}
