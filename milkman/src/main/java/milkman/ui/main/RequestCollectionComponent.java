package milkman.ui.main;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.reactfx.EventStreams;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListCell;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXToggleButton;
import com.jfoenix.controls.JFXTreeView;

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SettableTreeItem;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeItemBuilder;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import lombok.RequiredArgsConstructor;
import lombok.val;
import milkman.domain.Collection;
import milkman.domain.RequestContainer;
import milkman.domain.Searchable;
import milkman.ui.commands.UiCommand;
import milkman.utils.Event;

@Singleton
@RequiredArgsConstructor(onConstructor_ = { @Inject })
public class RequestCollectionComponent {

	public final Event<UiCommand> onCommand = new Event<UiCommand>();

	@FXML
	JFXTreeView<Node> collectionContainer;
	
	@FXML
	TextField searchField;

	Map<String, Boolean> expansionCache = new HashMap<>();

	public void display(List<Collection> collections) {

		collectionContainer.setShowRoot(false);
		
		List<TreeItem<Node>> entries = new LinkedList<>();
		for (Collection collection : collections) {
			entries.add(buildTree(collection));
		}

		
		val filteredList = new FilteredList<>(FXCollections.observableList(entries, c -> 
				((Collection)c.getValue().getUserData()).getRequests().stream()
				.map(r -> r.onDirtyChange)
				.collect(Collectors.toList())
				.toArray(new Observable[]{}))
		);
		SettableTreeItem<Node> root = new SettableTreeItem<Node>();
		root.setChildren(filteredList);
		Platform.runLater(() -> setFilterPredicate(filteredList, searchField.getText()));
		collectionContainer.setRoot(root);
		EventStreams.nonNullValuesOf(searchField.textProperty())
			.successionEnds(Duration.ofMillis(250))
			.subscribe(qry -> setFilterPredicate(filteredList, qry));

	}
	
	
	public TreeItem<Node> buildTree(Collection collection){
		TreeItem<Node> item = new TreeItem<Node>();
		HBox collEntry = createCollectionEntry(collection, item);
		item.setValue(collEntry);
		item.setExpanded(expansionCache.getOrDefault(collection.getName(), false));
		item.getValue().setUserData(collection);
		item.expandedProperty().addListener((obs, o, n) -> {
			expansionCache.put(collection.getName(), n);
		});
		for (RequestContainer r : collection.getRequests()) {
			TreeItem<Node> requestTreeItem = new TreeItem<Node>(createRequestEntry(collection, r));
			requestTreeItem.getValue().setUserData(r);
			item.getChildren().add(requestTreeItem);
		}
		return item;
	}
	

	private HBox createCollectionEntry(Collection collection, TreeItem<Node> item) {
		Label collectionName = new Label(collection.getName());
		HBox.setHgrow(collectionName, Priority.ALWAYS);
		
		
		
		JFXButton starringBtn = new JFXButton();
		starringBtn.getStyleClass().add("btn-starring");
		starringBtn.setGraphic(collection.isStarred() ? new FontAwesomeIconView(FontAwesomeIcon.STAR, "1em") : new FontAwesomeIconView(FontAwesomeIcon.STAR_ALT, "1em"));
		starringBtn.setOnAction( e -> {
			collection.setStarred(!collection.isStarred());
			starringBtn.setGraphic(collection.isStarred() ? new FontAwesomeIconView(FontAwesomeIcon.STAR, "1em") : new FontAwesomeIconView(FontAwesomeIcon.STAR_ALT, "1em"));
		});
		
		
		HBox hBox = new HBox(new FontAwesomeIconView(FontAwesomeIcon.FOLDER_ALT, "1.5em"), collectionName, starringBtn);

		MenuItem deleteEntry = new MenuItem("Delete");
		deleteEntry.setOnAction(e -> onCommand.invoke(new UiCommand.DeleteCollection(collection)));
		
		MenuItem renameEntry = new MenuItem("Rename");
		renameEntry.setOnAction(e -> onCommand.invoke(new UiCommand.RenameCollection(collection)));
		
		MenuItem exportEntry = new MenuItem("Export");
		exportEntry.setOnAction(e -> onCommand.invoke(new UiCommand.ExportCollection(collection)));
		
		
		ContextMenu ctxMenu = new ContextMenu(renameEntry, exportEntry, deleteEntry);

		hBox.setOnMouseClicked(e -> {
			
//			//we have to find out in a hackish way if we gonna expand the cell here or collapse
//			//because jfxListView does not fire the respective listeners correctly
//			if (hBox.getParent().getParent().getParent() instanceof JFXListCell) {
//				JFXListCell cell  = (JFXListCell) hBox.getParent().getParent().getParent();
//				boolean oldExpandedState = cell.isExpanded();
//				expansionCache.put(collection.getName(), !oldExpandedState);
//			}
			if (e.getButton() == MouseButton.PRIMARY) {
				item.setExpanded(!item.isExpanded());
				e.consume();
			}
			if (e.getButton() == MouseButton.SECONDARY) {
				ctxMenu.show(hBox, e.getScreenX(), e.getScreenY());
				e.consume();
			}
		});
		return hBox;
	}

	private void setFilterPredicate(FilteredList<TreeItem<Node>> filteredList,
			String searchTerm) {
		if (searchTerm != null && searchTerm.length() > 0)
			filteredList.setPredicate(o -> {
				Object userData = o.getValue().getUserData();
				if (userData instanceof Collection)
					if (((Collection) userData).isStarred())
						return true;
				
				return ((Searchable)userData).match(searchTerm);
			});
		else
			filteredList.setPredicate(o -> true);
	}


	
	private Node createRequestEntry(Collection collection, RequestContainer request) {
		Label requestType = new Label(request.getType());
		requestType.getStyleClass().add("request-type");
		Label button = new Label(request.getName());

		MenuItem renameEntry = new MenuItem("Rename");
		renameEntry.setOnAction(e -> onCommand.invoke(new UiCommand.RenameRequest(request)));

		
		MenuItem exportEntry = new MenuItem("Export");
		exportEntry.setOnAction(e -> onCommand.invoke(new UiCommand.ExportRequest(request)));
		
		
		MenuItem deleteEntry = new MenuItem("Delete");
		deleteEntry.setOnAction(e -> onCommand.invoke(new UiCommand.DeleteRequest(request, collection)));

		ContextMenu ctxMenu = new ContextMenu(renameEntry, exportEntry, deleteEntry);
		VBox vBox = new VBox(new HBox(requestType,button));
		vBox.getStyleClass().add("request-entry");
		vBox.setOnMouseClicked(e -> {
//			if (e.getButton() == MouseButton.PRIMARY)
//				onCommand.invoke(new UiCommand.LoadRequest(request.getId()));
//			else
			if (e.getButton() == MouseButton.PRIMARY) {
				onCommand.invoke(new UiCommand.LoadRequest(request.getId()));
				e.consume();
			}
			if (e.getButton() == MouseButton.SECONDARY) {
				ctxMenu.show(vBox, e.getScreenX(), e.getScreenY());
				e.consume();
			}
				
		});
		return vBox;
	}

	@FXML public void clearSearch() {
		searchField.clear();
	}

}
