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
import mdpa.gdpr.metamodel.contextproperties.Expression;
import mdpa.gdpr.metamodel.contextproperties.SAFAnnotation;
import mdpa.gdpr.metamodel.contextproperties.ScopeDependentAssessmentFact;
import mdpa.gdpr.metamodel.contextproperties.ScopeDependentAssessmentFacts;
import mdpa.gdpr.metamodel.contextproperties.ScopeSet;
import mdpa.laf.referencemodel.LAF.AssessmentFact;
import mdpa.laf.referencemodel.LAF.LegalContext;

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
	private Optional<ScopeDependentAssessmentFacts> optScopeDependentAssessmentFacts = Optional.empty();
	
	private Map<String, Processing> id2ProcessingMap = new HashMap<>();
	private Map<String, Purpose> id2PurposeMap = new HashMap<>();
	private Map<String, LegalBasis> id2LegalBasisMap = new HashMap<>();
	private Map<String, Data> id2DataMap = new HashMap<>();
	private Map<String, NaturalPerson> id2NaturalPersonMap = new HashMap<>();
	private Map<String, Controller> id2ControllerMap = new HashMap<>();
	private Map<String, ThirdParty> id2ThirdPartyMap = new HashMap<>();
	
	private Map<String, ScopeDependentAssessmentFact> id2ScopeDependentAssessmentFact = new HashMap<>();
	private Map<String, Expression> id2Expression = new HashMap<>();
	private Map<String, SAFAnnotation> id2SAFAnnotation = new HashMap<>();
	private Map<String, ScopeSet> id2ScopeSet = new HashMap<>();
	private Map<AssessmentFact, List<SAFAnnotation>> annotatedElement2SAFAnnotation = new HashMap<>();
	
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
		this.legalAssessmentFacts.getActions().stream()
			.filter(Processing.class::isInstance)
			.map(Processing.class::cast)
			.forEach(process -> this.id2ProcessingMap.put(process.getId(), process));
		this.legalAssessmentFacts.getObjects().stream()
			.filter(Data.class::isInstance)
			.map(Data.class::cast)
			.forEach(data -> this.id2DataMap.put(data.getId(), data));
		for(LegalContext context : this.legalAssessmentFacts.getContext()) {
			if(context instanceof Purpose) {
				this.id2PurposeMap.put(context.getId(), (Purpose) context);
			} else if (context instanceof LegalBasis) {
				this.id2LegalBasisMap.put(context.getId(), (LegalBasis) context);
			} else if (context instanceof NaturalPerson) {
				this.id2NaturalPersonMap.put(context.getId(), (NaturalPerson) context);
			}
		}
		for(Role role : this.legalAssessmentFacts.getSubjects()) {
			if (role instanceof Controller) {
				this.id2ControllerMap.put(role.getId(), (Controller) role);
			} else if (role instanceof ThirdParty) {
				this.id2ThirdPartyMap.put(role.getId(), (ThirdParty) role);
			} else {
				// should never occur and is currently not mapped
			}
		}

		if(optScopeDependentAssessmentFacts.isPresent()) {
			ScopeDependentAssessmentFacts scopeDependentAssessmentFacts = optScopeDependentAssessmentFacts.get();
			for(ScopeDependentAssessmentFact fact : scopeDependentAssessmentFacts.getScopeDependentAssessmentFact()) {
				this.id2ScopeDependentAssessmentFact.put(fact.getId(), fact);
				for(Expression expression : fact.getExpression()) {
					this.id2Expression.put(expression.getId(), expression);
				}
			}
			for(SAFAnnotation safAnnotation : scopeDependentAssessmentFacts.getSafAnnotation()) {
				this.id2SAFAnnotation.put(safAnnotation.getId(), safAnnotation);
				for(ScopeSet scopeSet : safAnnotation.getScopeSet()) {
					this.id2ScopeSet.put(scopeSet.getId(), scopeSet);
				}
				AssessmentFact annotatedElement = safAnnotation.getAnnotatedElement();
				if(!this.annotatedElement2SAFAnnotation.containsKey(annotatedElement)) {
					this.annotatedElement2SAFAnnotation.put(annotatedElement, new ArrayList<>());
				}
				this.annotatedElement2SAFAnnotation.get(annotatedElement).add(safAnnotation);
			}
		}
	}

	public List<SAFAnnotation> getSAFAnnotations(AssessmentFact element) {
		List<SAFAnnotation> annotations = this.annotatedElement2SAFAnnotation.get(element);
		if(annotations == null) {
			return List.of();
		} else {
			return annotations;
		}
	}

	public Collection<Role> getInvolvedParties() {
		return this.legalAssessmentFacts.getSubjects();
	}

	public Collection<Purpose> getPurposes() {
		return this.id2PurposeMap.values();
	}
	
	public Collection<LegalBasis> getLegalBases() {
		return this.id2LegalBasisMap.values();
	}
	
	public LegalAssessmentFacts getLegalAssessmentFacts() {
		return this.legalAssessmentFacts;
	}
	
	public Optional<ScopeDependentAssessmentFacts> getScopeDependentAssessmentFacts() {
		return this.optScopeDependentAssessmentFacts;
	}
		
	private void loadGDPRModel(URI gdprModelURI) {
		this.legalAssessmentFacts = (LegalAssessmentFacts) this.loadResource(gdprModelURI);		
	}
	
	private void loadContextPropertiesModel(URI contextPropertiesModelURI) {
		this.optScopeDependentAssessmentFacts = Optional.of((ScopeDependentAssessmentFacts) this.loadResource(contextPropertiesModelURI));
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
