package at.fhhagenberg.sqelevator.controller;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

import at.fhhagenberg.sqelevator.ElevatorExample;
import at.fhhagenberg.sqelevator.model.Elevator;
import at.fhhagenberg.sqelevator.model.Floor;
import at.fhhagenberg.sqelevator.views.ElevatorsListViewCell;
import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Alert.AlertType;
import javafx.util.Callback;
import sqelevator.IElevator;

public class ElevatorControlPanel {
 
	@FXML
	private ListView<Floor> floors;
	@FXML
	private ElevatorFloorController floorsController;
 
	@FXML
	private ListView<Elevator> lvElevators;
 
	protected List<Elevator> elevators = new ArrayList<>();
	protected ListProperty<Elevator> listPropertyElevators = new SimpleListProperty<>();
 
	private List<ElevatorsListViewCell> cells = new ArrayList<>();

	private IElevator elevatorSystem;
	private long clockTime; 

	private List<Integer> requestTargetQueue = new ArrayList<>();
	private List<Integer> elevatorTargetQueue = new ArrayList<>();

	public void init(IElevator elevatorSystem) {
		try {
			this.elevatorSystem = elevatorSystem;

			floorsController.init(elevatorSystem);

			for (int i = 0; i <= elevatorSystem.getElevatorNum() - 1; i++) {
				elevators.add(initElevatorFromSystem(new Elevator(i)));
			}

			lvElevators.itemsProperty().bind(listPropertyElevators);
			lvElevators.setCellFactory(new Callback<ListView<Elevator>, ListCell<Elevator>>() {

				@Override
				public ListCell<Elevator> call(ListView<Elevator> param) {
					ElevatorsListViewCell cell = new ElevatorsListViewCell(elevatorSystem);
					cells.add(cell);
					return cell;
				}
			});

			listPropertyElevators.set(FXCollections.observableArrayList(elevators));
		} catch (RemoteException e) {
			e.printStackTrace();
		}
	}

	public Runnable updateRunnable = new Runnable() {
		public void run() {

			try {
				if (clockTime != elevatorSystem.getClockTick()) {
					// Go throw all elevators and reload their data from elevator system
					// Refreshes GUI automatically through binding
					for (Elevator elevator : elevators) {
						initElevatorFromSystem(elevator);
					}

					// Refresh floor list
					floorsController.refreshFloors();

					// Refresh elevator floor lists
					for (ElevatorsListViewCell cell : cells) {
						cell.refreshFloors();
					}
				}

				clockTime = elevatorSystem.getClockTick();

				// check if new targets were pressed, elevators or floors
				isNewTargetPressed();

				// setting target
				for (Elevator elevator : elevators) {
					if (elevator.getAutomatic()) {

						// no elevator targets -> take element from floor queue
						noTarget(elevator);

						if (elevatorSystem.getElevatorFloor(elevator.getNumber()) == elevatorSystem
								.getTarget(elevator.getNumber())
								&& elevatorSystem.getElevatorDoorStatus(elevator.getNumber()) == 1) {

							canGetNextTarget(elevator);
						}
					}
				}
			} catch (RemoteException e) {
				ElevatorExample.pollingExecutor.shutdown();
				alertConnectionLost();
			}
		}

		/**
		 * @param elevator
		 * @throws RemoteException
		 */
		private void canGetNextTarget(Elevator elevator) throws RemoteException {
			if (elevator.getNextTarget() == -1) {

				if (elevatorTargetQueue.contains(elevatorSystem.getTarget(elevator.getNumber()))) {
					int toRemove = elevatorTargetQueue
							.indexOf(elevatorSystem.getTarget(elevator.getNumber()));
					elevatorTargetQueue.remove(toRemove);

					toRemove = requestTargetQueue.indexOf(elevator.getNextTarget());
					if (toRemove != -1) {
						requestTargetQueue.remove(toRemove);
					}
				}

			} else {
				if (requestTargetQueue.contains(elevator.getNextTarget())) {
					int toRemove = requestTargetQueue.indexOf(elevator.getNextTarget());
					requestTargetQueue.remove(toRemove);
				}

				if (elevatorTargetQueue.contains(elevator.getNextTarget())) {
					int toRemove = elevatorTargetQueue.indexOf(elevator.getNextTarget());
					elevatorTargetQueue.remove(toRemove);
				} else {
					elevator.removeTargetFromList();
				}
			}
		}

		/**
		 * @param elevator
		 * @throws RemoteException
		 */
		private void noTarget(Elevator elevator) throws RemoteException {
			if (elevator.getNextTarget() == -1) {
				if (!requestTargetQueue.isEmpty()) {

					for (int i = 0; i < requestTargetQueue.size(); i++) {
						if (!elevatorTargetQueue.contains(requestTargetQueue.get(i))) {
							elevator.insertTarget(-1, requestTargetQueue.get(i));
							elevatorTargetQueue.add(requestTargetQueue.get(i));
							elevatorSystem.setTarget(elevator.getNumber(), elevator.getNextTarget());
							break;
						}
					}
				}
			} else {
				if (elevatorSystem.getTarget(elevator.getNumber()) != elevator.getNextTarget()) {
					elevatorSystem.setTarget(elevator.getNumber(), elevator.getNextTarget());
				}
			}
		}

		/**
		 * @throws RemoteException
		 */
		private void isNewTargetPressed() throws RemoteException {
			for (int i = 0; i < elevatorSystem.getFloorNum(); i++) {

				// floor button was pressed
				if (elevatorSystem.getFloorButtonDown(i) || elevatorSystem.getFloorButtonUp(i)) {

					if (!requestTargetQueue.contains(i)) {
						// floors to send elevator to
						requestTargetQueue.add(i);
					}
				}

				for (int j = 0; j < elevators.size(); j++) {
					if (elevatorSystem.getElevatorButton(j, i)) {
						insertIntoTargetQueue(j, i);
					}
				}
			}
		}
	};
	
	public static void alertConnectionLost() {
		Alert alert = new Alert(AlertType.INFORMATION);

		alert.setTitle("Connection lost!");
		alert.setHeaderText("The connection to the elevator simulator got lost!");
		alert.setContentText("The application will be closed...");

		alert.showAndWait();

		System.exit(0);
	}

	private Elevator initElevatorFromSystem(Elevator elevator) throws RemoteException {
		int number = elevator.getNumber();

		int payload = elevatorSystem.getElevatorWeight(number);
		int speed = elevatorSystem.getElevatorSpeed(number);
		int doorStatus = elevatorSystem.getElevatorDoorStatus(number);
		int currentElevLocation = elevatorSystem.getElevatorPosition(number);

		elevator.setWeight(payload);
		elevator.setSpeed(speed);
		elevator.setDoorStatus(doorStatus);
		elevator.setCurrentElePosition(currentElevLocation);

		return elevator;
	}

	private void insertIntoTargetQueue(int elevatorNum, int target) throws RemoteException {

		if (elevators.get(elevatorNum).getNextTarget() != -1) {
			// elevator goes down
			if (elevatorSystem.getElevatorFloor(elevatorNum) > elevators.get(elevatorNum).getNextTarget()) {

				// requested floor is between target and current position -> stop on the way
				if (target < elevatorSystem.getElevatorFloor(elevatorNum)
						&& target > elevators.get(elevatorNum).getNextTarget()) {
					elevators.get(elevatorNum).insertTarget(0, target);
					// targetQueue.add(0, target);
				} else {
					// giving index -1 appends the target as last element
					elevators.get(elevatorNum).insertTarget(-1, target);
				}

				// elevator goes up
			} else {
				// requested floor is between target and current position -> stop on the way
				if (target > elevatorSystem.getElevatorFloor(elevatorNum)
						&& target < elevators.get(elevatorNum).getNextTarget()) {
					elevators.get(elevatorNum).insertTarget(0, target);
				} else {
					// giving index -1 appends the target as last element
					elevators.get(elevatorNum).insertTarget(-1, target);
				}
			}
		} else {
			elevators.get(elevatorNum).insertTarget(-1, target);
		}
	}
}
