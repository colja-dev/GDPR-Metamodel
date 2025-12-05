package mdpa.gdpr.metamodel.api;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import mdpa.gdpr.metamodel.GDPR.Controller;
import mdpa.gdpr.metamodel.GDPR.Data;
import mdpa.gdpr.metamodel.GDPR.LegalAssessmentFacts;
import mdpa.gdpr.metamodel.GDPR.LegalBasis;
import mdpa.gdpr.metamodel.GDPR.NaturalPerson;
import mdpa.gdpr.metamodel.GDPR.Processing;
import mdpa.gdpr.metamodel.GDPR.Purpose;
import mdpa.gdpr.metamodel.GDPR.Role;
import mdpa.gdpr.metamodel.GDPR.ThirdParty;
import mdpa.gdpr.metamodel.contextproperties.ContextAnnotation;
import mdpa.gdpr.metamodel.contextproperties.ContextDependentProperties;
import mdpa.gdpr.metamodel.contextproperties.Property;
import mdpa.gdpr.metamodel.contextproperties.PropertyAnnotation;
import mdpa.gdpr.metamodel.contextproperties.PropertyValue;
import mdpa.laf.referencemodel.LAF.AssessmentFact;

import org.eclipse.emf.common.EMFPlugin;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;

import tools.mdsd.library.standalone.initialization.StandaloneInitializationException;
import tools.mdsd.library.standalone.initialization.StandaloneInitializerBuilder;

public class GDPRMetamodelApi {
	private static final String PLUGIN_PATH = "mdpa.gdpr.metamodel.api";

	private ResourceSet resources = new ResourceSetImpl();
	
	private LegalAssessmentFacts legalAssessmentFacts = null;
	private Optional<ContextDependentProperties> optContextDependentProperties = Optional.empty();
	
	private Map<String, Processing> id2ProcessingMap = new HashMap<>();
	private Map<String, Purpose> id2PurposeMap = new HashMap<>();
	private Map<String, LegalBasis> id2LegalBasisMap = new HashMap<>();
	private Map<String, Data> id2DataMap = new HashMap<>();
	private Map<String, NaturalPerson> id2NaturalPersonMap = new HashMap<>();
	private Map<String, Controller> id2ControllerMap = new HashMap<>();
	private Map<String, ThirdParty> id2ThirdPartyMap = new HashMap<>();
	
	private Map<String, Property> id2Property = new HashMap<>();
	private Map<String, PropertyValue> id2PropertyValue = new HashMap<>();
	private Map<String, PropertyAnnotation> id2PropertyAnnotation = new HashMap<>();
	private Map<String, ContextAnnotation> id2ContextAnnotation = new HashMap<>();
	private Map<AssessmentFact, List<PropertyAnnotation>> annotatedElement2PropertyAnnotation = new HashMap<>();
	
	public GDPRMetamodelApi(URI gdprModelPath, Optional<URI> contextPropertiesModel) {
		if(EMFPlugin.IS_ECLIPSE_RUNNING) {
			initStandalone();
		}
		System.out.println("Loading GDPR model instance at " + gdprModelPath);
		loadGDPRModel(gdprModelPath);
		if(contextPropertiesModel.isPresent()) {
			System.out.println("Loading context property annotations model instance at " + contextPropertiesModel.get());
			loadContextPropertiesModel(contextPropertiesModel.get());
		}
		
		System.out.println("Resolving resources.");
		resolveResources();
		System.out.println("Initializing mappings.");
		initializeMappings();
	}
	
	public void initializeMappings() {
		for(Processing process : this.legalAssessmentFacts.getProcessing()) {
			this.id2ProcessingMap.put(process.getId(), process);
		}
		for(Purpose purpose : this.legalAssessmentFacts.getPurposes()) {
			this.id2PurposeMap.put(purpose.getId(), purpose);
		}
		for(LegalBasis basis : this.legalAssessmentFacts.getLegalBases()) {
			this.id2LegalBasisMap.put(basis.getId(), basis);
		}
		for(Data data : this.legalAssessmentFacts.getData()) {
			this.id2DataMap.put(data.getId(), data);
		}
		for(Role role : this.legalAssessmentFacts.getInvolvedParties()) {
			if(role instanceof NaturalPerson) {
				this.id2NaturalPersonMap.put(role.getId(), (NaturalPerson) role);
			} else if (role instanceof Controller) {
				this.id2ControllerMap.put(role.getId(), (Controller) role);
			} else if (role instanceof ThirdParty) {
				this.id2ThirdPartyMap.put(role.getId(), (ThirdParty) role);
			} else {
				// should never occur and is currently not mapped
			}
		}
		
		if(optContextDependentProperties.isPresent()) {
			ContextDependentProperties contextDependentProperties = optContextDependentProperties.get();
			for(Property property : contextDependentProperties.getProperty()) {
				this.id2Property.put(property.getId(), property);
				for(PropertyValue propertyValue : property.getPropertyvalue()) {
					this.id2PropertyValue.put(propertyValue.getId(), propertyValue);
				}
			}
			for(PropertyAnnotation propertyAnnotation : contextDependentProperties.getPropertyannotation()) {
				this.id2PropertyAnnotation.put(propertyAnnotation.getId(), propertyAnnotation);
				for(ContextAnnotation contextAnnotation : propertyAnnotation.getContextannotation()) {
					this.id2ContextAnnotation.put(contextAnnotation.getId(), contextAnnotation);
				}
				AssessmentFact annotatedElement = propertyAnnotation.getAnnotatedElement();
				if(!this.annotatedElement2PropertyAnnotation.containsKey(annotatedElement)) {
					this.annotatedElement2PropertyAnnotation.put(annotatedElement, new ArrayList<>());
				}
				this.annotatedElement2PropertyAnnotation.get(annotatedElement).add(propertyAnnotation);
			}
		}
	}
	
	public List<PropertyAnnotation> getPropertyAnnotations(AssessmentFact element) {
		List<PropertyAnnotation> annotations = this.annotatedElement2PropertyAnnotation.get(element);
		if(annotations == null) {
			return List.of();
		} else {
			return annotations;
		}
	}
	
	public Collection<Role> getInvolvedParties() {
		return this.legalAssessmentFacts.getInvolvedParties();
	}
	
	public Collection<Purpose> getPurposes() {
		return this.legalAssessmentFacts.getPurposes();
	}
	
	public Collection<LegalBasis> getLegalBases() {
		return this.id2LegalBasisMap.values();
	}
	
	public LegalAssessmentFacts getLegalAssessmentFacts() {
		return this.legalAssessmentFacts;
	}
	
	public Optional<ContextDependentProperties> getContextDependentProperties() {
		return this.optContextDependentProperties;
	}
		
	private void loadGDPRModel(URI gdprModelURI) {
		this.legalAssessmentFacts = (LegalAssessmentFacts) this.loadResource(gdprModelURI);		
	}
	
	private void loadContextPropertiesModel(URI contextPropertiesModelURI) {
		this.optContextDependentProperties = Optional.of((ContextDependentProperties) this.loadResource(contextPropertiesModelURI));		
	}
	
	private void resolveResources() {
		List<Resource> loadedResources = null;
		do {
			loadedResources = new ArrayList<>(this.resources.getResources());
			loadedResources.forEach(it->EcoreUtil.resolveAll(it));
		} while (loadedResources.size() != this.resources.getResources().size());
	}
	
	private EObject loadResource(URI modelURI) {
		Resource resource = this.resources.getResource(modelURI, true);
		if (resource == null) {
			throw new IllegalArgumentException(String.format("Model with URI %s could not be loaded", modelURI));
		} else if (resource.getContents().isEmpty()) {
			throw new IllegalArgumentException(String.format("Model with URI %s is empty", modelURI));
		}
		return resource.getContents().get(0);
	}
	
    private boolean initStandalone() {
        try {
            StandaloneInitializerBuilder.builder()
                .registerProjectURI(GDPRMetamodelApi.class, GDPRMetamodelApi.PLUGIN_PATH)
                .build()
                .init();
            return true;

        } catch (StandaloneInitializationException e) {
            e.printStackTrace();
            return false;
        }
    }
	
    private URI createRelativePluginURIFromProjectPath(String relativePath) {
        String path = Paths.get(relativePath)
            .toString();
        return URI.createPlatformPluginURI(path, false);
    }
    
    private URI createRelativePluginURIFromAbsolutePath(String absolutePath) {
    	return URI.createPlatformPluginURI(absolutePath, false);
    }
}
