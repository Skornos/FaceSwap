package fit.vutbr.faceswap;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.opencv.core.Rect;

import fit.vutbr.faceswap.HungarianAlgorithm;
import fit.vutbr.faceswap.CameraPreview.FaceRect;

import android.util.Log;

public class HungarianMatrix {
	final String TAG = "HungarianMatrix";
	
	float[][] euclidean_matrix;
	
	public HungarianMatrix(List<FaceRect> facesArray_prev_frame, List<FaceRect> facesArray_curr_frame) {
		
		fillMatrix(facesArray_prev_frame, facesArray_curr_frame);
		setDummyValues();
		//printMatrix();
		
		
	}
	
	public List<FaceRect> orderByAssociations(List<FaceRect> facesArray) {
		FaceRect[] facesArray_ordered;
		
		String output_str = "";
		int[][] order_indexes = computeAssociations();
		
		facesArray_ordered = new FaceRect[facesArray.size()];
		
		/*for (int[] i : order_indexes) {
			for (int j : i) {
				output_str += j + " ";
			}
			output_str += "\n";
		}
		*/
		//Log.d(TAG, output_str);
		
		int j = 0;
		for (int i=0; i < order_indexes.length; i++) {
			if (order_indexes[i][0] < facesArray.size()) {
				facesArray_ordered[j] = facesArray.get(order_indexes[i][0]);
				j++;
			}
		}
		
		return new ArrayList<FaceRect>(Arrays.asList(facesArray_ordered));
	}
	
	private int[][] computeAssociations() {
		HungarianAlgorithm h = new HungarianAlgorithm();
		
		return h.computeAssignments(euclidean_matrix);
	}
	
	private void setDummyValues() {
		float max = (float) -1.0;
		
		for (float[] r : euclidean_matrix)
			for (float c : r)
				if (c > max)
					max = c;
		
		for (int i=0; i < euclidean_matrix.length; i++) 
			for (int j=0; j < euclidean_matrix.length; j++)
				if (euclidean_matrix[i][j] < 0.0) 
					euclidean_matrix[i][j] = max;
				
	}
	
	// naplnim matici Euklidovskymi vzdalenostmi mezi body
	// radky: body aktualniho snimku
	// sloupce: body z predchoziho snimku
	private void fillMatrix(List<FaceRect> facesArray_prev_frame, List<FaceRect> facesArray_curr_frame) {
		//Log.d(TAG, "Prev: " + facesArray_prev_frame.length);
		//Log.d(TAG, "Curr: " + facesArray_curr_frame.length);
		
		// vytvorim ctvercovou matici
		if (facesArray_prev_frame.size() >= facesArray_curr_frame.size()) {
			euclidean_matrix = new float[facesArray_prev_frame.size()][facesArray_prev_frame.size()];
		}
		else {
			euclidean_matrix = new float[facesArray_curr_frame.size()][facesArray_curr_frame.size()];
		}
		
		// naplnim matici -1
		for (float[] row:euclidean_matrix)
			Arrays.fill(row, (float)-1.0);
		
		for (int i=0; i < facesArray_curr_frame.size(); i++) {
			Rect face_curr = facesArray_curr_frame.get(i).getRect();
			
			for (int j=0; j < facesArray_prev_frame.size(); j++) {
				Rect face_prev = facesArray_prev_frame.get(j).getRect();
				
				int diffX = face_prev.x - face_curr.x;
				int diffY = face_prev.y - face_curr.y;
				
				euclidean_matrix[i][j] = (float) Math.sqrt(diffX*diffX + diffY*diffY);
			}
		}
	}
	
	private void printMatrix() {
		String matrix_output = "";
		
		for (float[] i:euclidean_matrix) {
			for (float j:i) {
				matrix_output += j + " ";
			}
			matrix_output += "\n";
		}
		
		Log.d(TAG, matrix_output);
	}
}
