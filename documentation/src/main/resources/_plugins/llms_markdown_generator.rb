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

require 'set'

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

# Validates that llms.txt - which is checked in fully-formed (absolute
# .html.md URLs and descriptions already baked in, so it's directly useful to
# an LLM reading the repo source, not only the deployed site) - hasn't
# drifted from the pages it links to. Fails the build rather than silently
# shipping a stale link or description.
module LlmsTxtIndex
  RELEASE_NOTES_PREFIX = 'release_notes_'
  DOC_PAGES_PATH = 'pages/ditto/'

  def self.doc_link_regex(site)
    %r{^- \[[^\]]+\]\(#{Regexp.escape(site.config['url'].to_s)}/([\w.\-]+)\.html\.md\)}
  end

  # Slugs (without ".html") that llms.txt currently links to.
  def self.listed_slugs(site, raw)
    regex = doc_link_regex(site)
    raw.each_line.filter_map { |line| regex.match(line)&.[](1) }.to_set
  end

  # Finds the release_notes_* page with the highest X.Y.Z title. Filenames are
  # not safely sortable (the numbering scheme changed once patch versions hit
  # two digits, e.g. release_notes_394.md == 3.9.4 but release_notes_3812.md ==
  # 3.8.12), so this parses the title instead, which is consistently dotted.
  def self.resolve_latest_release_notes(site)
    versioned = site.pages.filter_map do |page|
      permalink = page.data['permalink']
      next unless permalink.to_s.start_with?(RELEASE_NOTES_PREFIX)

      match = /Release notes (\d+)\.(\d+)\.(\d+)\b/.match(page.data['title'].to_s)
      next unless match

      [[match[1].to_i, match[2].to_i, match[3].to_i], page]
    end
    return nil if versioned.empty?

    versioned.max_by { |version, _page| version }.last
  end

  def self.find_by_permalink(site, permalink)
    site.pages.find { |page| page.data['permalink'] == permalink }
  end

  # Checks every internal doc link in llms.txt against the live site: the
  # linked page must still exist, and a release-notes link must point at the
  # actual latest release. Returns a list of human-readable problems; empty
  # means llms.txt is accurate as checked in.
  def self.validate(site, raw)
    errors = []
    doc_link = doc_link_regex(site)
    latest_release_notes = resolve_latest_release_notes(site)

    raw.each_line do |line|
      match = doc_link.match(line)
      next unless match

      slug = match[1]
      permalink = "#{slug}.html"

      if find_by_permalink(site, permalink).nil?
        errors << "llms.txt links to '#{permalink}', but no page has that permalink (dead link)"
        next
      end

      next unless slug.start_with?(RELEASE_NOTES_PREFIX)

      if latest_release_notes.nil?
        errors << "llms.txt links to '#{permalink}' as the release notes entry, but no release_notes_* page has a parseable X.Y.Z title"
      elsif latest_release_notes.data['permalink'] != permalink
        errors << "llms.txt's release notes link points to '#{permalink}', but the latest release is " \
                   "'#{latest_release_notes.data['permalink']}' (#{latest_release_notes.data['title']}) - update the link"
      end
    end

    errors
  end

  # Doc pages that exist but aren't linked from llms.txt at all. This is
  # advisory only (which pages deserve a spot in the curated index is an
  # editorial call, not a correctness bug) - historical release_notes_* pages
  # are excluded since only the current latest is ever meant to be linked.
  def self.unlisted_pages(site, raw)
    listed = listed_slugs(site, raw)

    site.pages.select do |page|
      permalink = page.data['permalink']
      permalink && permalink.end_with?('.html') &&
        page.ext == '.md' &&
        page.relative_path.start_with?(DOC_PAGES_PATH) &&
        !permalink.start_with?(RELEASE_NOTES_PREFIX) &&
        !listed.include?(permalink.delete_suffix('.html'))
    end.map { |page| page.data['permalink'] }.sort
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

  llms_txt_source = File.join(site.source, 'llms.txt')
  if File.exist?(llms_txt_source)
    raw_llms_txt = File.read(llms_txt_source, encoding: 'utf-8')
    errors = LlmsTxtIndex.validate(site, raw_llms_txt)

    unless errors.empty?
      Jekyll.logger.error "LlmsTxt:", "#{errors.size} problem(s) found - llms.txt is out of sync with the site:"
      errors.each { |error| Jekyll.logger.error "LlmsTxt:", "  #{error}" }
      raise "llms.txt is out of sync with the site: #{errors.size} problem(s). See log above."
    end

    unlisted = LlmsTxtIndex.unlisted_pages(site, raw_llms_txt)
    unless unlisted.empty?
      Jekyll.logger.warn "LlmsTxt:", "#{unlisted.size} doc page(s) not listed in llms.txt (informational, not a failure):"
      unlisted.each { |permalink| Jekyll.logger.warn "LlmsTxt:", "  #{permalink}" }
    end
  end
end
