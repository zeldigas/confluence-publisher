require 'asciidoctor'
require 'asciidoctor/extensions'

class JiraIssue < Asciidoctor::Extensions::InlineMacroProcessor
  use_dsl

  named :jira
  name_positional_attributes 'server'

  def process parent, target, attrs
    key = target
    jira_macro = %(
    <ac:structured-macro ac:name="jira" ac:schema-version="1">
      <ac:parameter ac:name="key">#{key}</ac:parameter>
    </ac:structured-macro>
    )
    create_inline parent, :quoted, jira_macro
  end
end