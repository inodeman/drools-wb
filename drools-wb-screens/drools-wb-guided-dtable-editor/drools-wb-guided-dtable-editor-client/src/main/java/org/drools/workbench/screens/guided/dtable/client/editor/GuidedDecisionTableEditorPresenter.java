/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.drools.workbench.screens.guided.dtable.client.editor;

import java.util.List;
import javax.enterprise.context.Dependent;
import javax.enterprise.event.Event;
import javax.enterprise.event.Observes;
import javax.enterprise.inject.New;
import javax.inject.Inject;

import com.google.gwt.user.client.ui.IsWidget;
import org.drools.workbench.models.guided.dtable.shared.model.GuidedDecisionTable52;
import org.drools.workbench.screens.guided.dtable.client.type.GuidedDTableResourceType;
import org.drools.workbench.screens.guided.dtable.model.GuidedDecisionTableEditorContent;
import org.drools.workbench.screens.guided.dtable.service.GuidedDecisionTableEditorService;
import org.guvnor.common.services.shared.metadata.MetadataService;
import org.guvnor.common.services.shared.validation.model.ValidationMessage;
import org.guvnor.common.services.shared.version.events.RestoreEvent;
import org.jboss.errai.common.client.api.RemoteCallback;
import org.jboss.errai.common.client.api.Caller;
import org.drools.workbench.models.commons.shared.oracle.PackageDataModelOracle;
import org.kie.workbench.common.widgets.client.callbacks.HasBusyIndicatorDefaultErrorCallback;
import org.kie.workbench.common.widgets.client.menu.FileMenuBuilder;
import org.kie.workbench.common.widgets.client.popups.file.CommandWithCommitMessage;
import org.kie.workbench.common.widgets.client.popups.file.SaveOperationService;
import org.kie.workbench.common.widgets.client.popups.validation.ValidationPopup;
import org.kie.workbench.common.widgets.client.resources.i18n.CommonConstants;
import org.kie.workbench.common.widgets.configresource.client.widget.bound.ImportsWidgetPresenter;
import org.kie.workbench.common.widgets.metadata.client.callbacks.MetadataSuccessCallback;
import org.kie.workbench.common.widgets.metadata.client.widget.MetadataWidget;
import org.kie.workbench.common.widgets.viewsource.client.callbacks.ViewSourceSuccessCallback;
import org.kie.workbench.common.widgets.viewsource.client.screen.ViewSourceView;
import org.uberfire.backend.vfs.Path;
import org.uberfire.lifecycle.IsDirty;
import org.uberfire.lifecycle.OnClose;
import org.uberfire.lifecycle.OnMayClose;
import org.uberfire.lifecycle.OnSave;
import org.uberfire.lifecycle.OnStartup;
import org.uberfire.client.annotations.WorkbenchEditor;
import org.uberfire.client.annotations.WorkbenchMenu;
import org.uberfire.client.annotations.WorkbenchPartTitle;
import org.uberfire.client.annotations.WorkbenchPartView;
import org.uberfire.client.common.MultiPageEditor;
import org.uberfire.client.common.Page;
import org.uberfire.client.mvp.PlaceManager;
import org.uberfire.mvp.Command;
import org.uberfire.mvp.PlaceRequest;
import org.uberfire.workbench.events.NotificationEvent;
import org.uberfire.workbench.model.menu.Menus;

/**
 * Guided Decision Table Editor Presenter
 */
@Dependent
@WorkbenchEditor(identifier = "GuidedDecisionTableEditor", supportedTypes = { GuidedDTableResourceType.class })
public class GuidedDecisionTableEditorPresenter {

    @Inject
    private GuidedDecisionTableEditorView view;

    @Inject
    private ImportsWidgetPresenter importsWidget;

    @Inject
    private ViewSourceView viewSource;

    @Inject
    @New
    private MultiPageEditor multiPage;

    @Inject
    private Caller<GuidedDecisionTableEditorService> service;

    @Inject
    private Event<NotificationEvent> notification;

    @Inject
    private PlaceManager placeManager;

    @Inject
    private Caller<MetadataService> metadataService;

    @Inject
    @New
    private FileMenuBuilder menuBuilder;
    private Menus menus;

    private Path path;
    private PlaceRequest place;
    private boolean isReadOnly;

    private GuidedDecisionTable52 model;
    private PackageDataModelOracle oracle;

    @Inject
    private MetadataWidget metadataWidget;

    @OnStartup
    public void onStartup( final Path path,
                         final PlaceRequest place ) {
        this.path = path;
        this.place = place;
        this.isReadOnly = place.getParameter( "readOnly", null ) == null ? false : true;
        makeMenuBar();

        view.showBusyIndicator( CommonConstants.INSTANCE.Loading() );

        multiPage.addWidget( view,
                             CommonConstants.INSTANCE.EditTabTitle() );

        multiPage.addPage( new Page( viewSource,
                                     CommonConstants.INSTANCE.SourceTabTitle() ) {
            @Override
            public void onFocus() {
                viewSource.showBusyIndicator( CommonConstants.INSTANCE.Loading() );
                service.call( new ViewSourceSuccessCallback( viewSource ),
                              new HasBusyIndicatorDefaultErrorCallback( viewSource ) ).toSource( path,
                                                                                                 view.getContent() );
            }

            @Override
            public void onLostFocus() {
                viewSource.clear();
            }
        } );

        multiPage.addWidget( importsWidget,
                             CommonConstants.INSTANCE.ConfigTabTitle() );

        multiPage.addPage( new Page( metadataWidget,
                                     CommonConstants.INSTANCE.MetadataTabTitle() ) {
            @Override
            public void onFocus() {
                metadataWidget.showBusyIndicator( CommonConstants.INSTANCE.Loading() );
                metadataService.call( new MetadataSuccessCallback( metadataWidget,
                                                                   isReadOnly ),
                                      new HasBusyIndicatorDefaultErrorCallback( metadataWidget ) ).getMetadata( path );
            }

            @Override
            public void onLostFocus() {
                //Nothing to do
            }
        } );

        loadContent();
    }

    private void loadContent() {
        service.call( getModelSuccessCallback(),
                      new HasBusyIndicatorDefaultErrorCallback( view ) ).loadContent( path );
    }

    private void makeMenuBar() {
        if ( isReadOnly ) {
            menus = menuBuilder.addRestoreVersion( path ).build();
        } else {
            menus = menuBuilder
                    .addSave( new Command() {
                        @Override
                        public void execute() {
                            onSave();
                        }
                    } )
                    .addCopy( path )
                    .addRename( path )
                    .addDelete( path )
                    .addValidate( onValidate() )
                    .build();
        }
    }

    private RemoteCallback<GuidedDecisionTableEditorContent> getModelSuccessCallback() {
        return new RemoteCallback<GuidedDecisionTableEditorContent>() {

            @Override
            public void callback( final GuidedDecisionTableEditorContent response ) {
                model = response.getRuleModel();
                oracle = response.getDataModel();
                oracle.filter( model.getImports() );

                view.setContent( path,
                                 oracle,
                                 model,
                                 response.getWorkItemDefinitions(),
                                 isReadOnly );
                importsWidget.setContent( oracle,
                                          model.getImports(),
                                          isReadOnly );

                view.hideBusyIndicator();
            }
        };
    }

    private Command onValidate() {
        return new Command() {
            @Override
            public void execute() {
                service.call( new RemoteCallback<List<ValidationMessage>>() {
                    @Override
                    public void callback( final List<ValidationMessage> results ) {
                        if ( results == null || results.isEmpty() ) {
                            notification.fire( new NotificationEvent( CommonConstants.INSTANCE.ItemValidatedSuccessfully(),
                                                                      NotificationEvent.NotificationType.SUCCESS ) );
                        } else {
                            ValidationPopup.showMessages( results );
                        }
                    }
                } ).validate( path,
                              view.getContent() );
            }
        };
    }

    @OnSave
    public void onSave() {
        if ( isReadOnly ) {
            view.alertReadOnly();
            return;
        }

        new SaveOperationService().save( path,
                                         new CommandWithCommitMessage() {
                                             @Override
                                             public void execute( final String commitMessage ) {
                                                 view.showBusyIndicator( CommonConstants.INSTANCE.Saving() );
                                                 service.call( getSaveSuccessCallback(),
                                                               new HasBusyIndicatorDefaultErrorCallback( view ) ).save( path,
                                                                                                                        view.getContent(),
                                                                                                                        metadataWidget.getContent(),
                                                                                                                        commitMessage );
                                             }
                                         } );
    }

    private RemoteCallback<Path> getSaveSuccessCallback() {
        return new RemoteCallback<Path>() {

            @Override
            public void callback( final Path path ) {
                view.setNotDirty();
                view.hideBusyIndicator();
                metadataWidget.resetDirty();
                notification.fire( new NotificationEvent( CommonConstants.INSTANCE.ItemSavedSuccessfully() ) );
            }
        };
    }

    @IsDirty
    public boolean isDirty() {
        return view.isDirty();
    }

    @OnClose
    public void onClose() {
        this.path = null;
    }

    @OnMayClose
    public boolean checkIfDirty() {
        if ( isDirty() ) {
            return view.confirmClose();
        }
        return true;
    }

    @WorkbenchPartTitle
    public String getTitle() {
        return "Guided Decision Table [" + path.getFileName() + "]";
    }

    @WorkbenchPartView
    public IsWidget getWidget() {
        return multiPage;
    }

    @WorkbenchMenu
    public Menus getMenus() {
        return menus;
    }

    public void onRestore( @Observes RestoreEvent restore ) {
        if ( path == null || restore == null || restore.getPath() == null ) {
            return;
        }
        if ( path.equals( restore.getPath() ) ) {
            loadContent();
            notification.fire( new NotificationEvent( CommonConstants.INSTANCE.ItemRestored() ) );
        }
    }

}
