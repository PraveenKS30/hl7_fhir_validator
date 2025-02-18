package com.demo.hl7_fhir_validator.controller;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.parser.IParser;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ValidationResult;

import org.hl7.fhir.common.hapi.validation.support.*;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/fhir")
public class FHIRValidationController {

    private final FhirContext fhirContext = FhirContext.forR4();
    private final FhirValidator fhirValidator;

    public FHIRValidationController() {
        // **Step 1: Set up the Validation Support Chain**
        // This chain provides support for validating resources against standard profiles,
        // handling terminology validation, and generating snapshot versions of profiles.
        ValidationSupportChain validationSupportChain = new ValidationSupportChain();

        // Add default profile support (structures and data types)
        DefaultProfileValidationSupport defaultSupport = new DefaultProfileValidationSupport(fhirContext);
        validationSupportChain.addValidationSupport(defaultSupport);

        // Add in-memory terminology server support (code systems and value sets)
        InMemoryTerminologyServerValidationSupport terminologySupport = new InMemoryTerminologyServerValidationSupport(fhirContext);
        validationSupportChain.addValidationSupport(terminologySupport);

        // Add support for common code systems (e.g., SNOMED CT, LOINC)
        CommonCodeSystemsTerminologyService commonCodeSystems = new CommonCodeSystemsTerminologyService(fhirContext);
        validationSupportChain.addValidationSupport(commonCodeSystems);

        // Add snapshot generation support
        SnapshotGeneratingValidationSupport snapshotSupport = new SnapshotGeneratingValidationSupport(fhirContext);
        validationSupportChain.addValidationSupport(snapshotSupport);

        // Wrap the chain in a caching layer for performance
        CachingValidationSupport cachingValidationSupport = new CachingValidationSupport(validationSupportChain);

        // **Step 2: Create and Register the FhirInstanceValidator Module**
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(fhirContext);
        instanceValidator.setValidationSupport(cachingValidationSupport);
        
        // **Step 3: Initialize the FhirValidator and Register the Validator Module**
        fhirValidator = fhirContext.newValidator();
        fhirValidator.registerValidatorModule(instanceValidator);

    }



    @PostMapping("/validate")
    public ResponseEntity<String> validateFHIRResource(@RequestBody String jsonResource) {
        IParser parser = fhirContext.newJsonParser();
        Resource resource = (Resource) parser.parseResource(jsonResource);

        ValidationResult validationResult = fhirValidator.validateWithResult(resource);

        // Convert ValidationResult to OperationOutcome
        OperationOutcome operationOutcome = (OperationOutcome) validationResult.toOperationOutcome();

        // Serialize OperationOutcome to JSON
        String outcomeJson = parser.encodeResourceToString(operationOutcome);

        // Set the appropriate HTTP headers
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.valueOf("application/fhir+json"));

        // Determine the HTTP status code
        if (validationResult.isSuccessful()) {
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(outcomeJson);
        } else {
            return ResponseEntity.badRequest()
                    .headers(headers)
                    .body(outcomeJson);
        }
    }

}