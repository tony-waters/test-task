package uk.bit1.engagement.importer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CopyBlockParserTest {

  @Test
  void readsOnlyTheCopyBlocksAndSkipsEverythingElse() throws IOException {
    String dump =
        """
        SET client_encoding = 'UTF8';
        CREATE TABLE public.thing (a text, b text);
        COPY public.thing (a, b) FROM stdin;
        one\ttwo
        three\t\\N
        \\.
        ALTER TABLE ONLY public.thing ADD CONSTRAINT thing_pkey PRIMARY KEY (a);
        COPY public.other (x) FROM stdin;
        \\.
        """;

    Map<String, CopyBlock> blocks = parse(dump);

    assertThat(blocks).containsOnlyKeys("thing", "other");
    CopyBlock thing = blocks.get("thing");
    assertThat(thing.columns()).containsExactly("a", "b");
    assertThat(thing.rows()).hasSize(2);
    assertThat(thing.namedRows().get(0).get("b")).isEqualTo("two");
    assertThat(thing.namedRows().get(1).get("b")).isNull();
    assertThat(blocks.get("other").rows()).isEmpty();
  }

  @Test
  void keepsEmptyStringsDistinctFromNull() {
    assertThat(CopyBlockParser.splitRow("\t\\N\t")).containsExactly("", null, "");
  }

  @Test
  void unescapesCopyTextFormat() {
    assertThat(CopyBlockParser.unescape("a\\tb\\nc\\\\d")).isEqualTo("a\tb\nc\\d");
    assertThat(CopyBlockParser.unescape("\\101\\x42")).isEqualTo("AB");
    assertThat(CopyBlockParser.unescape("O\\'Brien")).isEqualTo("O'Brien");
  }

  @Test
  void rejectsARowWithTheWrongNumberOfFields() {
    String dump = "COPY public.thing (a, b) FROM stdin;\nonly-one\n\\.\n";

    assertThatThrownBy(() -> parse(dump))
        .isInstanceOf(DumpFormatException.class)
        .hasMessageContaining("line 2")
        .hasMessageNotContaining("only-one");
  }

  @Test
  void rejectsAnUnterminatedBlock() {
    assertThatThrownBy(() -> parse("COPY public.thing (a) FROM stdin;\nx\n"))
        .isInstanceOf(DumpFormatException.class);
  }

  @Test
  void parsesNaiveTimestampsAsUtc() throws IOException {
    CopyBlock.Row row =
        parse("COPY public.t (a, b, c) FROM stdin;\n2025-06-30 23:30:00.5\t2025-01-01 00:00:00\t\\N\n\\.\n")
            .get("t")
            .namedRows()
            .get(0);

    assertThat(row.utc("a").toInstant()).hasToString("2025-06-30T23:30:00.500Z");
    assertThat(row.utc("b").toInstant()).hasToString("2025-01-01T00:00:00Z");
    assertThat(row.utc("c")).isNull();
  }

  @Test
  void aValueThatDoesNotParseIsReportedWithoutTheValue() throws IOException {
    List<CopyBlock.Row> rows =
        parse(
                "COPY public.patient (dob, id, created) FROM stdin;\n"
                    + "1980-06-15\t00000000-0000-4000-8000-000000000001\t2025-01-01 00:00:00\n"
                    + "1980-13-15\tnot-a-uuid\tyesterday\n\\.\n")
            .get("patient")
            .namedRows();
    CopyBlock.Row bad = rows.get(1);

    assertThat(rows.get(0).date("dob")).isEqualTo(LocalDate.of(1980, 6, 15));
    assertThat(catchThrowable(() -> bad.date("dob")))
        .isInstanceOf(DumpFormatException.class)
        .hasMessage("COPY patient row 2, column dob: not a date")
        .hasNoCause();
    assertThat(catchThrowable(() -> bad.uuid("id")))
        .hasMessage("COPY patient row 2, column id: not a UUID")
        .hasNoCause();
    assertThat(catchThrowable(() -> bad.utc("created")))
        .hasMessage("COPY patient row 2, column created: not a timestamp")
        .hasNoCause();
  }

  private static Map<String, CopyBlock> parse(String text) throws IOException {
    return CopyBlockParser.parse(new BufferedReader(new StringReader(text)));
  }
}
