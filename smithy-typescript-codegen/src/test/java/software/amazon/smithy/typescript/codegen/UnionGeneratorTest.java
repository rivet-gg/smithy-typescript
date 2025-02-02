package software.amazon.smithy.typescript.codegen;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;

import org.junit.jupiter.api.Test;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.node.Node;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.UnionShape;

public class UnionGeneratorTest {
    @Test
    public void generatesTaggedUnions() {
        MemberShape memberA = MemberShape.builder()
                .id("com.foo#Example$A")
                .target("smithy.api#String")
                .build();
        MemberShape memberB = MemberShape.builder()
                .id("com.foo#Example$B")
                .target("smithy.api#Integer")
                .build();
        MemberShape memberC = MemberShape.builder()
                .id("com.foo#Example$C")
                .target("smithy.api#Boolean")
                .build();
        UnionShape unionShape = UnionShape.builder()
                .id("com.foo#Example")
                .addMember(memberA)
                .addMember(memberB)
                .addMember(memberC)
                .build();
        Model model = Model.assembler()
                .addImport(getClass().getResource("simple-service.smithy"))
                .addShapes(unionShape, memberA, memberB, memberC)
                .assemble()
                .unwrap();
        TypeScriptSettings settings = TypeScriptSettings.from(model, Node.objectNodeBuilder()
                .withMember("package", Node.from("example"))
                .withMember("packageVersion", Node.from("1.0.0"))
                .build());
        SymbolProvider symbolProvider = TypeScriptCodegenPlugin.createSymbolProvider(model, settings);
        TypeScriptWriter writer = new TypeScriptWriter("./Example");
        new UnionGenerator(model, symbolProvider, writer, unionShape).run();
        String output = writer.toString();

        // It generates a union of the possible variant types.
        assertThat(output, containsString("export type Example =\n"
                                          + "  | Example.AMember\n"
                                          + "  | Example.BMember\n"
                                          + "  | Example.CMember\n"
                                          + "  | Example.$UnknownMember"));

        // It generates a wrapping namespace.
        assertThat(output, containsString("export namespace Example {"));

        // It generates an unknown variant
        assertThat(output, containsString("export interface $UnknownMember {\n"
                                          + "    a?: never;\n"
                                          + "    b?: never;\n"
                                          + "    c?: never;\n"
                                          + "    $unknown: [string, any];\n"
                                          + "  }"));

        // It generates a variant for each member.
        assertThat(output, containsString("export interface AMember {\n"
                                          + "    a: string;\n"
                                          + "    b?: never;\n"
                                          + "    c?: never;\n"
                                          + "    $unknown?: never;\n"
                                          + "  }"));

        assertThat(output, containsString("export interface BMember {\n"
                                          + "    a?: never;\n"
                                          + "    b: number;\n"
                                          + "    c?: never;\n"
                                          + "    $unknown?: never;\n"
                                          + "  }"));

        assertThat(output, containsString("export interface CMember {\n"
                                          + "    a?: never;\n"
                                          + "    b?: never;\n"
                                          + "    c: boolean;\n"
                                          + "    $unknown?: never;\n"
                                          + "  }"));

        // It generates a visitor type.
        assertThat(output, containsString("export interface Visitor<T> {\n"
                                          + "    a: (value: string) => T;\n"
                                          + "    b: (value: number) => T;\n"
                                          + "    c: (value: boolean) => T;\n"
                                          + "    _: (name: string, value: any) => T;\n"
                                          + "  }"));

        // It generates the actual visitor function.
        assertThat(output, containsString("export const visit = <T>(\n"
                                          + "    value: Example,\n"
                                          + "    visitor: Visitor<T>\n"
                                          + "  ): T => {\n"
                                          + "    if (value.a !== undefined) return visitor.a(value.a);\n"
                                          + "    if (value.b !== undefined) return visitor.b(value.b);\n"
                                          + "    if (value.c !== undefined) return visitor.c(value.c);\n"
                                          + "    return visitor._(value.$unknown[0], value.$unknown[1]);\n"
                                          + "  }"));
    }
}
