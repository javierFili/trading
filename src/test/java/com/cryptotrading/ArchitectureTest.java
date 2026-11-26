package com.cryptotrading;

import com.cryptotrading.exchange.domain.AccountClient;
import com.cryptotrading.exchange.domain.OrderClient;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable architecture rules. A failing rule here is a design violation, not a flaky test.
 */
class ArchitectureTest {

    private static final String ROOT = "com.cryptotrading";
    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    void modulesAreFreeOfCycles() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void domainIsFrameworkFree() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework..", "jakarta.persistence..", "org.hibernate..",
                        "..application..", "..infrastructure..", "..presentation..")
                .check(classes);
    }

    @Test
    void applicationDoesNotDependOnAdaptersOrPresentation() {
        noClasses().that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "..presentation..")
                .allowEmptyShould(true)
                .check(classes);
    }

    @Test
    void onlyExchangeModuleKnowsBinance() {
        noClasses().that().resideOutsideOfPackage(ROOT + ".exchange..")
                .should().dependOnClassesThat().resideInAPackage(ROOT + ".exchange.infrastructure..")
                .check(classes);
    }

    @Test
    void onlyExecutionModuleMayPlaceOrCancelOrders() {
        noClasses().that().resideOutsideOfPackages(ROOT + ".execution..", ROOT + ".exchange..")
                .should().dependOnClassesThat().areAssignableTo(OrderClient.class)
                .check(classes);
    }

    @Test
    void strategiesCannotTouchAccountsOrdersOrRisk() {
        noClasses().that().resideInAPackage(ROOT + ".strategies..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".risk..", ROOT + ".trading..", ROOT + ".execution..", ROOT + ".portfolio..")
                .orShould().dependOnClassesThat().areAssignableTo(OrderClient.class)
                .orShould().dependOnClassesThat().areAssignableTo(AccountClient.class)
                .check(classes);
    }

    @Test
    void riskIsIndependentFromSignalProducers() {
        noClasses().that().resideInAPackage(ROOT + ".risk..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".strategies..", ROOT + ".signals..", ROOT + ".trading..", ROOT + ".execution..")
                .check(classes);
    }

    @Test
    void floatingPointIsNotUsedInFinancialModules() {
        classes().that().resideInAnyPackage(
                        ROOT + ".shared..", ROOT + ".market..", ROOT + ".indicators..", ROOT + ".strategies..",
                        ROOT + ".signals..", ROOT + ".risk..", ROOT + ".trading..", ROOT + ".execution..",
                        ROOT + ".portfolio..", ROOT + ".backtesting..", ROOT + ".exchange..")
                .should(notUseFloatingPoint())
                .because("money and prices must use BigDecimal")
                .check(classes);
    }

    /**
     * ArchUnit does not report dependencies on primitive types, so a plain dependency rule would pass
     * vacuously. This condition inspects fields, signatures and every method/constructor call explicitly.
     */
    private static ArchCondition<JavaClass> notUseFloatingPoint() {
        return new ArchCondition<>("not use float/double") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaField field : javaClass.getFields()) {
                    report(events, javaClass, isFloating(field.getRawType()), field.getFullName());
                }
                for (JavaCodeUnit unit : javaClass.getCodeUnits()) {
                    report(events, javaClass, isFloating(unit.getRawReturnType()), unit.getFullName() + " returns");
                    unit.getRawParameterTypes().forEach(type ->
                            report(events, javaClass, isFloating(type), unit.getFullName() + " parameter"));
                    unit.getCallsFromSelf().forEach(call -> {
                        var target = call.getTarget();
                        boolean floating = isFloating(target.getOwner())
                                || target.getRawParameterTypes().stream().anyMatch(ArchitectureTest::isFloating)
                                || (target instanceof com.tngtech.archunit.core.domain.AccessTarget.MethodCallTarget m
                                    && isFloating(m.getRawReturnType()));
                        report(events, javaClass, floating, call.getDescription());
                    });
                }
            }
        };
    }

    private static boolean isFloating(JavaClass type) {
        String name = type.getName();
        return name.equals("double") || name.equals("float")
                || name.equals(Double.class.getName()) || name.equals(Float.class.getName());
    }

    private static void report(ConditionEvents events, JavaClass owner, boolean violated, String where) {
        if (violated) {
            events.add(SimpleConditionEvent.violated(owner, "floating point used: " + where));
        }
    }
}
