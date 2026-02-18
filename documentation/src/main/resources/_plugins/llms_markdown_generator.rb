#
# Copyright (c) 2026 Contributors to the Eclipse Foundation
#
# See the NOTICE file(s) distributed with this work for additional
# information regarding copyright ownership.
#
# This program and the accompanying materials are made available under the
# terms of the Eclipse Public License 2.0 which is available at
# http://www.eclipse.org/legal/epl-2.0
#
# SPDX-License-Identifier: EPL-2.0
#

# Jekyll plugin that generates clean .html.md versions of documentation pages
# for LLM consumption, supporting the llms.txt specification (https://llmstxt.org).
#
# For each page with a permalink like "basic-thing.html", this plugin outputs a
# "basic-thing.html.md" file containing the raw markdown with Liquid template
# tags transformed into readable markdown equivalents.

module LlmsMarkdown
  # Transform Liquid include tags and other Jekyll-specific syntax into clean markdown.
  def self.clean(content, title)
    result = content.dup

    # Add title as H1
    result.prepend("# #{title}\n\n") if title

    # Transform note/warning/tip/important includes to blockquotes
    result.gsub!(/\{%\s*include\s+(note|warning|tip|important)\.html\s+content="(.*?)"\s*%\}/m) do
      type = Regexp.last_match(1).capitalize
      text = Regexp.last_match(2).strip.gsub(/<br\s*\/?>/, "\n")
      "> **#{type}:** #{text}"
    end

    # Transform callout includes to blockquotes
    result.gsub!(/\{%\s*include\s+callout\.html\s+.*?content="(.*?)".*?\s*%\}/m) do
      text = Regexp.last_match(1).strip.gsub(/<br\s*\/?>/, "\n")
      "> #{text}"
    end

    # Transform image includes to markdown images
    result.gsub!(/\{%\s*include\s+image\.html\s+(.*?)\s*%\}/m) do
      params = parse_params(Regexp.last_match(1))
      alt = params['alt'] || ''
      file = params['file'] || ''
      img = "![#{alt}](images/#{file})"
      img += "\n*#{params['caption']}*" if params['caption']
      img
    end

    # Transform external_image includes to markdown images
    result.gsub!(/\{%\s*include\s+external_image\.html\s+(.*?)\s*%\}/m) do
      params = parse_params(Regexp.last_match(1))
      alt = params['alt'] || ''
      url = params['url'] || ''
      "![#{alt}](#{url})"
    end

    # Transform inline_image includes to markdown images
    result.gsub!(/\{%\s*include\s+inline_image\.html\s+(.*?)\s*%\}/m) do
      params = parse_params(Regexp.last_match(1))
      alt = params['alt'] || ''
      file = params['file'] || ''
      "![#{alt}](images/#{file})"
    end

    # Transform docson (JSON schema viewer) includes to schema links
    result.gsub!(/\{%\s*include\s+docson\.html\s+schema="([^"]*?)"\s*%\}/) do
      schema = Regexp.last_match(1)
      "[JSON Schema: #{schema}](../#{schema})"
    end

    # Transform file download includes to markdown links
    result.gsub!(/\{%\s*include\s+file\.html\s+(.*?)\s*%\}/m) do
      params = parse_params(Regexp.last_match(1))
      file_title = params['title'] || params['file'] || 'Download'
      file_name = params['file'] || ''
      "[#{file_title}](files/#{file_name})"
    end

    # Strip raw/endraw tags (used to escape Ditto placeholder syntax like {{ header:xxx }})
    result.gsub!(/\{%\s*raw\s*%\}/, '')
    result.gsub!(/\{%\s*endraw\s*%\}/, '')

    # Strip HTML tooltip wrappers, keep the visible text
    result.gsub!(/<a[^>]*data-toggle="tooltip"[^>]*>([^<]*)<\/a>/, '\1')

    # Resolve glossary variable references to plain text
    result.gsub!(/\{\{\s*site\.data\.glossary\.(\w+)\s*\}\}/, '\1')

    # Strip any remaining unrecognized include tags, leaving a comment
    result.gsub!(/\{%\s*include\s+(\S+)\s+.*?\s*%\}/m) do
      "<!-- include: #{Regexp.last_match(1)} -->"
    end

    result
  end

  # Parse key="value" parameters from a Liquid include tag.
  def self.parse_params(param_string)
    params = {}
    param_string.scan(/([\w-]+)="(.*?)"/m) do |key, value|
      params[key] = value
    end
    params
  end
end

Jekyll::Hooks.register :site, :post_write do |site|
  count = 0

  site.pages.each do |page|
    permalink = page.data['permalink']
    next unless permalink && permalink.end_with?('.html')
    next unless page.ext == '.md'

    # Read the original source file
    source_path = File.join(site.source, page.relative_path)
    next unless File.exist?(source_path)

    raw = File.read(source_path, encoding: 'utf-8')

    # Strip YAML front matter
    content = raw.sub(/\A---.*?---\s*/m, '')

    # Transform to clean markdown
    content = LlmsMarkdown.clean(content, page.data['title'])

    # Write .html.md version to output directory
    md_path = File.join(site.dest, "#{permalink}.md")
    FileUtils.mkdir_p(File.dirname(md_path))
    File.write(md_path, content, encoding: 'utf-8')
    count += 1
  end

  Jekyll.logger.info "LlmsMarkdown:", "Generated #{count} .html.md files for LLM consumption"
end
