package at.fhhagenberg.sqelevator.controller;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import at.fhhagenberg.sqelevator.model.Floor;
import at.fhhagenberg.sqelevator.views.ElevatorFloorStatusListViewCell;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.util.Callback;
import sqelevator.IElevator;

public class ElevatorFloorsManuelController {
	  
	@FXML private ListView<Floor> lvFloors;
	
	private IElevator elevatorSystem;
	private int elevatorNumber;
	 
	protected List<Floor> floors = new ArrayList<>();
	protected ListProperty<Floor> listPropertyFloors = new SimpleListProperty<>();
	
	public void init(IElevator elevatorSystem, int elevatorNumber) {
		try {
			this.elevatorSystem = elevatorSystem;
			this.elevatorNumber = elevatorNumber;
		
			for(int i = elevatorSystem.getFloorNum()-1; i >= 0; i--) {
				floors.add(new Floor(i));
			}
	
			lvFloors.itemsProperty().bind(listPropertyFloors);
			lvFloors.setCellFactory(new Callback<ListView<Floor>, ListCell<Floor>>() {
				
				@Override
				public ListCell<Floor> call(ListView<Floor> param) {
					return new ElevatorFloorStatusListViewCell(elevatorSystem, elevatorNumber);
				}
			});
	        
			listPropertyFloors.set(FXCollections.observableArrayList(floors));
			
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}
	
	public void refreshFloors() {
		lvFloors.refresh();
	}
}
